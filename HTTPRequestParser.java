import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class HTTPRequestParser {
    File resolvedFile;
    Path documentRoot, filePath, resolvedFilePath;
    String extension, fileName, method;

    Set<String> acceptedFileFormats = createSet();

    // using a private method avoids method leaks and anonymous classes
    private Set<String> createSet() {
        Set<String> set = new HashSet<>();
        set.add("html");
        set.add("gif");
        set.add("txt");
        set.add("jpg");
        set.add("jpeg");
        return set;
    }

    // constructor
    public HTTPRequestParser(String httpRequest, String docRoot) {
        String[] request = httpRequest.split("\\s");
        method = request[0];

        fileName = request[1].substring(1);
        extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        // handle 'GET /' which is the same as 'GET /index.html'
        if(extension.equals("")) {
            fileName = "index.html";
            extension = "html";
        }

        // resolve file paths and get file
        filePath = Paths.get(fileName);
        documentRoot = Paths.get(docRoot);
        resolvedFilePath = documentRoot.resolve(filePath);
        resolvedFile = resolvedFilePath.toFile();
    }

    // return a HTTPResponse object
    public HTTPResponse getHTTPResponse() {
        boolean fileExists = fileExists();
        boolean filePermissions = checkFilePermissions();
        boolean checkHTTPRequest = checkHTTPRequest();
        return new HTTPResponse(checkHTTPRequest, filePermissions, fileExists, resolvedFile, extension);
    }

    /*
    The following three functions are boolean helper functions that help determine error status codes (if there are errors)
    in HTTPResponse construction
     */
    private boolean fileExists() {
        return resolvedFile.exists();
    }

    private boolean checkFilePermissions() { return Files.isReadable(resolvedFilePath); }

    // checks if the method is not a GET, if the filename is mangled, and if file extension is supported (not exhaustive)
    private boolean checkHTTPRequest() {
        return (method.equals("GET") && fileName.lastIndexOf(".") != -1 && acceptedFileFormats.contains(extension));
    }
}

/* helper class for HTTPRequestParser --> HTTPRequestParser generates a HTTPResponse, and later on the Server can build
out the rest of the headers + body of the HTTPResponse
 */
class HTTPResponse {
    // keep these public for ease of access
    boolean isError;
    File fileToSend;
    String contentLength, contentType, date, errorBody, extension, statusCode;
    Map<String, String> mapExtensionToHttpField = createMap();

    // using a private method avoids method leaks and anonymous classes
    private static Map<String, String> createMap() {
        Map<String,String> myMap = new HashMap<String,String>();
        myMap.put("html", "index/html; charset=utf-8");
        myMap.put("gif", "image/gif");
        myMap.put("jpg", "image/jpeg");
        myMap.put("jpeg", "image/jpeg");
        myMap.put("txt", "text/plain");
        return myMap;
    }

    // simple but useful HTML strings to send error codes to browser
    public String error400Html = "<html><body>400 Bad Request</body></html>";
    public String error403Html = "<html><body>403 Forbidden</body></html>";
    public String error404Html = "<html><body>404 Not Found</body></html>";

    // useful data for setting Content-Length parameter in HTTP responses
    long error400Length = error400Html.getBytes(StandardCharsets.UTF_8).length;
    long error403Length = error403Html.getBytes(StandardCharsets.UTF_8).length;
    long error404Length = error404Html.getBytes(StandardCharsets.UTF_8).length;

    // Note: the full HTTP response header is not assembled until an error or successful request is determined later on
    public HTTPResponse(boolean checkHTTPRequest, boolean filePermissions, boolean fileExists, File file, String ext) {
        // request is bad --> 400 error
        if (!checkHTTPRequest) {
            statusCode = "400";
            isError = true;
        }
        // if file doesn't exist --> 404 error
        else if (!fileExists) {
            statusCode = "404";
            isError = true;
        }
        // if file isn't readable --> 403 error
        else if (!filePermissions) {
            statusCode = "403";
            isError = true;
        }
        // request is good to go --> 200 success
        else {
            statusCode = "200";
            isError = false;
        }
        // set other information for headers
        fileToSend = file;
        date = LocalDate.now() + " " + LocalTime.now();
        extension = ext;
    }

    // return HTML and select between 400, 403, and 404 errors
    public void generateErrorResponse() {
        contentType = "index/html; charset=utf-8";
        switch(statusCode) {
            case "400":
                errorBody = error400Html;
                contentLength = String.valueOf(error400Length);
                break;
            case "403":
                errorBody = error403Html;
                contentLength = String.valueOf(error403Length);
                break;
            case "404":
                errorBody = error404Html;
                contentLength = String.valueOf(error404Length);
                break;
        }
    }

    // set HTTP response headers of the successful request
    public void generateSuccessResponse() {
        contentType = mapExtensionToHttpField.get(extension);
        contentLength = String.valueOf(fileToSend.length());
    }

    /*
    This function servers as a workaround for a strange error encountered when trying to construct HTTP responses via
    String variable concatenations in the HTTPRequestParser + HTTPResponse classes. For whatever reason, using String variables
    concatenation appears to deform the HTTP response even logging them side-by-side appears the same.
    */
    public String generateHTTPResponseHeaders() {
        String response = "";
        //  weird case where concatenating contentType deforms HTTP response only for HTML responses,
        // but not for .txt, .jpg/.jpeg, and .gif
        switch(statusCode) {
            case "200":
                if (extension.equals("html")) {
                    response = "HTTP/1.0 200 OK\nContent-Length: " + contentLength + "\nContent-Type: text/html; charset=utf-8" +
                            "\nDate: " + date + "\n\n";
                } else {
                    response = "HTTP/1.0 200 OK\nContent-Length: " + contentLength + "\nContent-Type:" + contentType +
                            "\nDate: " + date + "\n\n";
                }
                break;
            case "400":
                response = "HTTP/1.0 400 Bad Request\nContent-Length: " + contentLength + "\nContent-Type: text/html; charset=utf-8" +
                        "\nDate: " + date +"\n\n";
                break;
            case "403":
                response = "HTTP/1.0 403 Forbidden\nContent-Length: " + contentLength + "\nContent-Type: text/html; charset=utf-8" +
                        "\nDate: " + date +"\n\n";
                break;
            case "404":
                response = "HTTP/1.0 404 Not Found\nContent-Length: " + contentLength + "\nContent-Type: text/html; charset=utf-8" +
                        "\nDate: " + date +"\n\n";
                break;
        }
        return response;
    }

    // Differentiates between sending either simple error HTML strings or reading files in "server"
    public boolean isErroneous() {
        return isError;
    }
}