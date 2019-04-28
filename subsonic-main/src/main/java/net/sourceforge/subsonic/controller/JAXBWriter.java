/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.controller;

import static org.springframework.web.bind.ServletRequestUtils.getStringParameter;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.io.IOUtils;
import org.eclipse.persistence.jaxb.JAXBContext;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.subsonic.restapi.Error;
import org.subsonic.restapi.ObjectFactory;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.ResponseStatus;

import net.kakadua.KakaduaUtil;
import net.sourceforge.subsonic.Logger;
import net.sourceforge.subsonic.util.StringUtil;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class JAXBWriter {

    private static final Logger LOG = Logger.getLogger(JAXBWriter.class);

    private final javax.xml.bind.JAXBContext jaxbContext;
    private final DatatypeFactory datatypeFactory;
    private final String restProtocolVersion;

    public JAXBWriter() {
        try {
            jaxbContext = JAXBContext.newInstance(Response.class);
            datatypeFactory = DatatypeFactory.newInstance();
            restProtocolVersion = getRESTProtocolVersion();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    private Marshaller createXmlMarshaller() throws JAXBException {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_ENCODING, StringUtil.ENCODING_UTF8);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        return marshaller;
    }

    private Marshaller createJsonMarshaller() throws JAXBException {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_ENCODING, StringUtil.ENCODING_UTF8);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json");
        marshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, true);
        return marshaller;
    }

    private String getRESTProtocolVersion() throws Exception {
        InputStream in = null;
        try {
            in = StringUtil.class.getResourceAsStream("/subsonic-rest-api.xsd");
            Document document = new SAXBuilder().build(in);
            Attribute version = document.getRootElement().getAttribute("version");
            return version.getValue();
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public String getRestProtocolVersion() {
        return restProtocolVersion;
    }
    
    public void updateVersionsFile(){
        String current = "1.1.beta1";
        String versionCheck = current;
		//KakaduaUtil.http_get_contents("http://booksonic.org/versioncheck.php?server&v="+current);
        KakaduaUtil.file_write("versionCheck", versionCheck);
    }
    
    public Response createResponse(boolean ok) {
        Response response = new ObjectFactory().createResponse();
        response.setStatus(ok ? ResponseStatus.OK : ResponseStatus.FAILED);
        response.setVersion(restProtocolVersion);
       /*File versionsFile = new File("versions");
        if(!versionsFile.exists()){
        	updateVersionsFile();
        }
        if(versionsFile.lastModified() < System.currentTimeMillis()-3600000){
        	updateVersionsFile();
        }
        String versionCheck = "up_to_date";
        versionCheck = KakaduaUtil.file_read("versionCheck");
        */
        response.setBooksonic("");
        return response;
    }

    public void writeResponse(HttpServletRequest request, HttpServletResponse httpResponse, Response jaxbResponse) throws Exception {

        String format = getStringParameter(request, "f", "xml");
        String jsonpCallback = request.getParameter("callback");
        boolean json = "json".equals(format);
        boolean jsonp = "jsonp".equals(format) && jsonpCallback != null;
        Marshaller marshaller;

        if (json) {
            marshaller = createJsonMarshaller();
            httpResponse.setContentType("application/json");
        } else if (jsonp) {
            marshaller = createJsonMarshaller();
            httpResponse.setContentType("text/javascript");
        } else {
            marshaller = createXmlMarshaller();
            httpResponse.setContentType("text/xml");
        }

        httpResponse.setCharacterEncoding(StringUtil.ENCODING_UTF8);

        try {
            StringWriter writer = new StringWriter();
            if (jsonp) {
                writer.append(jsonpCallback).append('(');
            }
            marshaller.marshal(new ObjectFactory().createSubsonicResponse(jaxbResponse), writer);
            if (jsonp) {
                writer.append(");");
            }
            httpResponse.getWriter().append(writer.getBuffer());
        } catch (Exception x) {
            LOG.error("Failed to marshal JAXB", x);
            throw x;
        }
    }

    public void writeErrorResponse(HttpServletRequest request, HttpServletResponse response,
            RESTController.ErrorCode code, String message) throws Exception {
        Response res = createResponse(false);
        Error error = new Error();
        res.setError(error);
        error.setCode(code.getCode());
        error.setMessage(message);
        writeResponse(request, response, res);
    }

    public XMLGregorianCalendar convertDate(Date date) {
        if (date == null) {
            return null;
        }

        GregorianCalendar c = new GregorianCalendar();
        c.setTime(date);
        return datatypeFactory.newXMLGregorianCalendar(c).normalize();
    }
}
