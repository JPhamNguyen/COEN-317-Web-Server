import java.io.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Server {
    // main attributes
    ServerSocketChannel server;
    Selector selector;
    SelectionKey key;
    ByteBuffer readBuffer, writeBuffer;
    int writeBufferSize;
    String documentRoot;
    HTTPRequestParser parser;
    HTTPResponse httpResponse;
    Logger logger = Logger.getLogger("ServerLog");
    FileHandler fileHandler;

    // initialize the server and hold important information
    public Server(String docRoot, int port) throws IOException {
        documentRoot = docRoot;
        server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port));
        server.configureBlocking(false);
        selector = Selector.open();
        key = server.register(selector, SelectionKey.OP_ACCEPT);
        writeBufferSize = 10240;
        readBuffer = ByteBuffer.allocate(2048);
        writeBuffer = ByteBuffer.allocate(writeBufferSize);

        fileHandler = new FileHandler("ServerLog.log", true);
        logger.addHandler(fileHandler);
        SimpleFormatter formatter = new SimpleFormatter();
        fileHandler.setFormatter(formatter);
        logger.info("Successfully created server logs\n");
    }

    /*
    This function runs the server indefinitely until the user quits the server via CMD+C, and will keep iterating over its
    set of SelectionKeys that represent different channels/events
     */
    public void run() throws IOException {
        while (true) {
            selector.select();
            Set<SelectionKey> keySet = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keySet.iterator();

            while (iterator.hasNext()) {
                key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                } if (key.isReadable()) {
                    SocketChannel readClient = (SocketChannel) key.channel();
                    readClient.read(readBuffer);
                    String message = new String(readBuffer.array()).trim();
                    logger.info("Request from client below... \n" + message + "\n");
                    readBuffer.clear();
                    parser = new HTTPRequestParser(message, documentRoot);
                    httpResponse = parser.getHTTPResponse();
                    readClient.register(selector, SelectionKey.OP_WRITE);
                } if (key.isWritable()) {
                    // TODO: Make sure to test 403 errors (go to cmd line and set random file permission to hidden/write-only
                    SocketChannel writerClient = (SocketChannel) key.channel();
                    // depending on response status, build out rest of response headers + message-body accordingly
                    if (httpResponse.isErroneous()) {
                        httpResponse.generateErrorResponse();
                        String response = httpResponse.generateHTTPResponseHeaders();
                        logger.info("\nHTTP response from server to client's previous request: \n" + response);
                        writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                        try {
                            writerClient.write(writeBuffer);
                            writeBuffer.compact();
                            writeBuffer = ByteBuffer.wrap(httpResponse.errorBody.getBytes(StandardCharsets.UTF_8));
                            writerClient.write(writeBuffer);
                            writeBuffer.compact();
                        } catch (IOException error) {
                            System.out.println(error.toString());
                        }
                    } else {
                        httpResponse.generateSuccessResponse();
                        String response = httpResponse.generateHTTPResponseHeaders();
                        logger.info("HTTP response from server to client's previous request: \n" + response);
                        writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                        try {
                            writerClient.write(writeBuffer);
                            writeBuffer.compact();
                            sendFile(httpResponse, writerClient);
                        } catch (IOException error) {
                            System.out.println(error.toString());
                        }
                    }
                    writerClient.close();
                    System.out.println("Connection closed. Please make another request to open another connection\n");
                }
            }
        }
    }

    // contained sending the file in a function to handle any exceptions that may occur
    public void sendFile(HTTPResponse response, SocketChannel channel) throws IOException {
        RandomAccessFile reader = new RandomAccessFile(response.fileToSend, "r");
        FileChannel writeChannel = reader.getChannel();
        writeChannel.transferTo(0, writeChannel.size(), channel);
    }

    public static void main(String[] args) throws IOException {
        // Only checks length of arguments provided. Assumes that the proper args have been specified otherwise
        if (args.length != 4) {
            throw new IOException("Length of args specified is not equal to 4." +
                    "\n Please specify the args as: " +
                    "-document_root [document_root (String)] -port [port number (int)]");
        }
        // assign the port and file system path
        int port;
        String documentRoot;
        try {
            port = Integer.parseInt(args[3]);
            documentRoot = args[1];
        } catch (Exception error) {
            throw new ClassCastException(error.toString());
        }
        // init server and run
        Server server = new Server(documentRoot, port);
        server.run();
    }
}
