/**
 * Copyright (C) 2009  HungryHobo@mail.i2p
 * 
 * The GPG fingerprint for HungryHobo@mail.i2p is:
 * 6DD3 EAA2 9990 29BC 4AD2 7486 1E2C 7B61 76DC DC12
 * 
 * This file is part of I2P-Bote.
 * I2P-Bote is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * I2P-Bote is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with I2P-Bote.  If not, see <http://www.gnu.org/licenses/>.
 */

package i2p.bote.fileencryption;

import static i2p.bote.fileencryption.FileEncryptionConstants.KDF_PARAMETERS;
import static i2p.bote.fileencryption.FileEncryptionConstants.SALT_LENGTH;
import i2p.bote.Configuration;
import i2p.bote.Util;
import i2p.bote.service.I2PBoteThread;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.i2p.I2PAppContext;

/**
 * Stores a password in memory so the user doesn't have to re-enter it.
 * Also caches key derivation parameters (salt and <code>scrypt</code> parameters)
 * so the key derivation function only needs to run once.
 */
public class PasswordCache extends I2PBoteThread implements PasswordHolder {
    private byte[] password;
    private DerivedKey derivedKey;
    private long lastReset;
    private Configuration configuration;
    private Lock passwordLock = new ReentrantLock();
    
    /**
     * Creates a new <code>PasswordCache</code>.
     * @param configuration
     */
    public PasswordCache(Configuration configuration) {
        super("PasswordCache");
        this.configuration = configuration;
    }
    
    /**
     * Sets the password.
     * @param password
     */
    public synchronized void setPassword(byte[] password) {
        // wait until the lock is released
        lockPassword();
        resetExpiration();
        this.password = password;
        // clear the old key
        if (derivedKey != null) {
            derivedKey.clear();
            derivedKey = null;
        }
        unlockPassword();
    }
    
    /**
     * Reads salt and <code>scrypt</code> parameters from the cache file, or chooses
     * a new salt array if the file doesn't exist. The encryption key is then computed
     * and the variable <code>derivedKey</code> is populated.
     * @throws IOException 
     * @throws GeneralSecurityException 
     */
    private void createDerivedKey() throws IOException, GeneralSecurityException {
        byte[] salt = null;
        derivedKey = null;
        
        // read salt + scrypt parameters from file if available
        File derivParamFile = configuration.getKeyDerivationParametersFile();
        if (derivParamFile.exists())
            derivedKey = FileEncryptionUtil.getEncryptionKey(password, derivParamFile);
        
        // if necessary, create a new salt and key and write the derivation parameters to the cache file
        if (derivedKey==null || !derivedKey.scryptParams.equals(KDF_PARAMETERS)) {
            I2PAppContext appContext = I2PAppContext.getGlobalContext();
            salt = new byte[SALT_LENGTH];
            appContext.random().nextBytes(salt);
            
            DataOutputStream outputStream = null;
            try {
                byte[] key = FileEncryptionUtil.getEncryptionKey(password, salt, KDF_PARAMETERS);
                derivedKey = new DerivedKey(salt, KDF_PARAMETERS, key);
                outputStream = new DataOutputStream(new FileOutputStream(derivParamFile));
                outputStream.write(salt);
                KDF_PARAMETERS.writeTo(outputStream);
            }
            finally {
                if (outputStream != null)
                    outputStream.close();
            }
        }
    }
    
    /**
     * Returns the cached password. If the password is not in the cache, the default password (if no
     * password is set) or <code>null</code> (if a password is set) is returned.
     * @return The cached password or <code>null</code> if the password is not in the cache
     */
    public synchronized byte[] getPassword() {
        resetExpiration();
        if (password==null && !configuration.getPasswordFile().exists())
            return FileEncryptionConstants.DEFAULT_PASSWORD;
        else
            return password;
    }
    
    @Override
    public synchronized DerivedKey getKey() throws IOException, GeneralSecurityException {
        if (derivedKey == null)
            createDerivedKey();
        return derivedKey;
    }

    /** Acquires a lock that prevents the password from expiring or being changed. */
    public void lockPassword() {
        passwordLock.lock();
    }
    
    /** The counterpart to {@link #lockPassword()} */
    public void unlockPassword() {
        passwordLock.unlock();
    }
    
    private void resetExpiration() {
        lastReset = System.currentTimeMillis();
    }
    
    /** Returns <code>true</code> if the password is currently cached. */
    public boolean isPasswordInCache() {
        return password != null;
    }
    
    /**
     * Clears the password if it is in the cache.
     */
    public void clear() {
        lockPassword();
        try {
            if (password == null)
                return;
            Util.zeroOut(password);
            password = null;
            if (derivedKey != null) {
                derivedKey.clear();
                derivedKey = null;
            }
        }
        finally {
            unlockPassword();
        }
    }
    
    /**
     * Clears the password after a certain time if {@link #getPassword()} hasn't been called.
     * @see Configuration#getPasswordCacheDuration()
     */
    @Override
    protected void doStep() throws InterruptedException {
        awaitShutdownRequest(1, TimeUnit.MINUTES);
        long durationMilliseconds = TimeUnit.MILLISECONDS.convert(configuration.getPasswordCacheDuration(), TimeUnit.MINUTES);
        boolean isEmpty = password==null || password.length==0;
        if (System.currentTimeMillis()>lastReset+durationMilliseconds && !isEmpty)   // cache empty passwords forever
            clear();
    }
}