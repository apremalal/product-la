/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.la.restapi;

import javax.activation.DataHandler;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.http.HttpHeaders;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * File Processing API
 */

@Path("/files")
public class FileProcessingApi {

    private static final Log logger = LogFactory.getLog(FileProcessingApi.class);

    @OPTIONS
    public Response options() {
        return Response.ok().header(HttpHeaders.ALLOW, "GET POST DELETE").build();
    }

    @POST
    @Path("/upload")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadLog(@Multipart("logGroup") String logGroup, @Multipart("logStream") String logStream,
                              @Multipart("description") String description, @Multipart("file") Attachment attachment){
        DataHandler dataHandler = attachment.getDataHandler();
        String fileName=null;
        try {
            InputStream stream = dataHandler.getInputStream();
            MultivaluedMap<String, String> map = attachment.getHeaders();
            fileName = getFileName(map);
            System.out.println("fileName Here" + fileName);
            String tempFolderLocation = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator
                    + "data" + File.separator + "analyzer-logs" + File.separator + logGroup + "-" + logStream ;

            File tempDir = new File(tempFolderLocation);
                if (!tempDir.exists()) {
                    FileUtils.forceMkdir(tempDir);
                }

            OutputStream out = new FileOutputStream(new File(tempFolderLocation + File.separator + fileName));
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = stream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            stream.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] logSourceInfo = {logGroup,logStream,fileName};
        return Response.ok(logSourceInfo).build();
    }

    private String getFileName(MultivaluedMap<String, String> header) {
        String[] contentDisposition = header.getFirst("Content-Disposition").split(";");
        for (String filename : contentDisposition) {
            if ((filename.trim().startsWith("filename"))) {
                String[] name = filename.split("=");
                String exactFileName = name[1].trim().replaceAll("\"", "");
                return exactFileName;
            }
        }
        return "unknown";
    }

    @GET
    @Path("getLogs")
    @Produces("application/json")
    @Consumes("application/json")
    public Response getLogs( @QueryParam("noOfLines") int noOfLines, @QueryParam("logGroup") String logGroup,
                             @QueryParam("logStream") String logStream, @QueryParam("fileName") String fileName) {
        Object [] logLines;

        logLines = readLogs(noOfLines, logGroup, logStream, fileName);

        return Response.ok(Arrays.copyOf(logLines, logLines.length, String[].class)).build();
    }

    private  Object[] readLogs(int noOfLines, String logGroup, String logStream, String fileName) {
        List<String> lines = new ArrayList();
        String tempFolderLocation = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator
                + "data" + File.separator + "analyzer-logs" + File.separator + logGroup + "-" + logStream;
        File file = new File(tempFolderLocation + File.separator +  fileName);
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            int offset=0;
            while (true) {
                String line = br.readLine();
                if (line != null && offset < noOfLines) {
                    lines.add(line);
                    offset ++;
                } else {
                    break;
                }
            }
            br.close();
        } catch (Exception ex) {
            logger.error("Error reading", ex);
        }
        return  lines.toArray();
    }
}
