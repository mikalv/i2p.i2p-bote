<%--
 Copyright (C) 2009  HungryHobo@mail.i2p
 
 The GPG fingerprint for HungryHobo@mail.i2p is:
 6DD3 EAA2 9990 29BC 4AD2 7486 1E2C 7B61 76DC DC12
 
 This file is part of I2P-Bote.
 I2P-Bote is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 
 I2P-Bote is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with I2P-Bote.  If not, see <http://www.gnu.org/licenses/>.
--%>

<%@ attribute name="date" type="java.util.Date" required="true" description="The date and/or time to display" %>
<%@ attribute name="type" required="false" description="Whether to print the date, the time, or both. See the time parameter of fmt:formatDate. The default is both." %>
<%@ attribute name="timeStyle" required="false" description="See the timeStyle parameter of fmt:formatDate" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="ib" uri="I2pBoteTags" %>

<c:if test="${!empty date}">
    <c:if test="${empty type}">
        <c:set var="type" value="both"/>
    </c:if>
    <fmt:formatDate value="${date}" var="date" type="${type}" timeStyle="${timeStyle}"/>
</c:if>
<c:if test="${empty date}">
    <ib:message key="Unknown" var="date"/>
</c:if>
${fn:escapeXml(date)}