package pb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pb.app.Whiteboard;
import pb.app.WhiteboardApp;
import pb.managers.IOThread;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 *
 * @author aaron
 *
 */
public class WhiteboardServer {

    private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());

    /**
     * Emitted by a client to tell the server that a board is being shared.
     * Argument must have the format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String shareBoard = "SHARE_BOARD";

    /**
     * Emitted by a client to tell the server that a board is no longer being
     * shared. Argument must have the format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String unshareBoard = "UNSHARE_BOARD";

    /**
     * The server emits this event:
     * <ul>
     * <li>to all connected clients to tell them that a board is being
     * shared</li>
     * <li>to a newly connected client, it emits this event several times, for
     * all boards that are currently known to be being shared</li>
     * </ul>
     * Argument has format "host:port:boardid"
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String sharingBoard = "SHARING_BOARD";

    /**
     * The server emits this event:
     * <ul>
     * <li>to all connected clients to tell them that a board is no longer
     * shared</li>
     * </ul>
     * Argument has format "host:port:boardid"
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String unsharingBoard = "UNSHARING_BOARD";

    /**
     * Emitted by the server to a client to let it know that there was an error
     * in a received argument to any of the events above. Argument is the error
     * message.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String error = "ERROR";

    public static final String disconnectPeer = "DISCONNECT_PEER";

    /**
     * Default port number.
     */
    private static int port = Utils.indexServerPort;

    private static void help(Options options) {
        String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
        String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("pb.IndexServer", header, options, footer, true);
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // set a nice log format
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");

        // parse command line options
        Options options = new Options();
        options.addOption("port", true, "server port, an integer");
        options.addOption("password", true, "password for server");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e1) {
            help(options);
        }

        if (cmd.hasOption("port")) {
            try {
                port = Integer.parseInt(cmd.getOptionValue("port"));
            } catch (NumberFormatException e) {
                System.out.println("-port requires a port number, parsed: " + cmd.getOptionValue("port"));
                help(options);
            }
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;

        if (cmd.hasOption("password")) {
            serverManager = new ServerManager(port, cmd.getOptionValue("password"));
        } else {
            serverManager = new ServerManager(port);
        }

        /**
         * TODO: Put some server related code here.
         */
        // start up the server
        Map<String, Endpoint>  peerInformation = new HashMap<>();
        Map<String, Endpoint>  sharedBoards = new HashMap<>();
        Map<String, Endpoint>  deletedBoard = new HashMap<>();


        serverManager.on(ServerManager.sessionStarted, (eventArgs) -> {
            Endpoint endpoint = (Endpoint) eventArgs[0];
            peerInformation.put(endpoint.getOtherEndpointId(), endpoint);

            log.info("一连上找人要板");
            log.info("" + sharedBoards.size());
            for (String key : sharedBoards.keySet()) {
                endpoint.emit(sharingBoard, key);
            }



            log.info("Client session started: " + endpoint.getOtherEndpointId());
            endpoint.on(shareBoard, (eventArgs2) -> {
                String sharedBoardName = (String) eventArgs2[0];
                sharedBoards.put(sharedBoardName, endpoint);

                for (String key : sharedBoards.keySet()) {
                    log.info("sdsadsdsd" + sharedBoards.get(key));
                }

                log.info("Received share request: " + sharedBoardName);

                for (String key : peerInformation.keySet()) {
                    if (!key.equals(endpoint.getOtherEndpointId())) {
                        peerInformation.get(key).emit(sharingBoard, sharedBoardName);
                    }
                }

            }).on(unshareBoard, (eventArgs4) -> {

                String unsharedBoardName = (String) eventArgs4[0];
                log.info("received unshare board" + unsharedBoardName);
//                log.info("******" + unsharedBoardName);
                sharedBoards.remove(unsharedBoardName);

                for (String key : peerInformation.keySet()) {
                    if (!key.equals(endpoint.getOtherEndpointId())) {
                        peerInformation.get(key).emit(unsharingBoard, unsharedBoardName);
                    }
                }
            }).on(error, (eventArgs5) -> {
                log.info("error??????????????????");
                endpoint.emit(error, "what the fuck????????????????????");
            });

        }).on(ServerManager.sessionStopped, (eventArgs) -> {
            Endpoint endpoint = (Endpoint) eventArgs[0];
            log.info("Client session ended: " + endpoint.getOtherEndpointId());



            for (String key : sharedBoards.keySet()) {
                if (endpoint == sharedBoards.get(key)) {
                    deletedBoard.put(key, endpoint);

                }
            }

            for (String key : deletedBoard.keySet()) {
                sharedBoards.remove(key);

                for (String key2 : peerInformation.keySet()) {
                    log.info("&&&&&&&");
                    log.info(key);
                    log.info(key2);
                    if (!key2.equals(endpoint.getOtherEndpointId())) {
                        log.info("真传了");
                        peerInformation.get(key2).emit(disconnectPeer, key);
                    }
                }
            }



            for (String key : peerInformation.keySet()) {

            }

        }).on(ServerManager.sessionError, (eventArgs) -> {
            Endpoint endpoint = (Endpoint) eventArgs[0];
            log.warning("Client session ended in error: " + endpoint.getOtherEndpointId());
        }).on(IOThread.ioThread, (eventArgs) -> {
            String peerport = (String) eventArgs[0];
            // we don't need this info, but let's log it
            log.info("using Internet address: " + peerport);
        });

        log.info("Whiteboard Server starting up");
        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();

    }

}
