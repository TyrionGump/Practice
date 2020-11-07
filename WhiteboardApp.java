package pb.app;

import pb.Client;
import pb.IndexServer;
import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {

    private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());

    /**
     * Emitted to another peer tos for the given board.
     * Argument must have format "host:port:boardid".
     * <ul> subscribe to update
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String listenBoard = "BOARD_LISTEN";

    /**
     * Emitted to another peer to unsubscribe to updates for the given board.
     * Argument must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String unlistenBoard = "BOARD_UNLISTEN";

    /**
     * Emitted to another peer to get the entire board data for a given board.
     * Argument must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String getBoardData = "GET_BOARD_DATA";

    /**
     * Emitted to another peer to give the entire board data for a given board.
     * Argument must have format "host:port:boardid%version%PATHS".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardData = "BOARD_DATA";

    /**
     * Emitted to another peer to add a path to a board managed by that peer.
     * Argument must have format "host:port:boardid%version%PATH". The numeric
     * value of version must be equal to the version of the board without the
     * PATH added, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

    /**
     * Emitted to another peer to indicate a new path has been accepted.
     * Argument must have format "host:port:boardid%version%PATH". The numeric
     * value of version must be equal to the version of the board without the
     * PATH added, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

    /**
     * Emitted to another peer to remove the last path on a board managed by
     * that peer. Argument must have format "host:port:boardid%version%". The
     * numeric value of version must be equal to the version of the board
     * without the undo applied, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

    /**
     * Emitted to another peer to indicate an undo has been accepted. Argument
     * must have format "host:port:boardid%version%". The numeric value of
     * version must be equal to the version of the board without the undo
     * applied, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

    /**
     * Emitted to another peer to clear a board managed by that peer. Argument
     * must have format "host:port:boardid%version%". The numeric value of
     * version must be equal to the version of the board without the clear
     * applied, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

    /**
     * Emitted to another peer to indicate an clear has been accepted. Argument
     * must have format "host:port:boardid%version%". The numeric value of
     * version must be equal to the version of the board without the clear
     * applied, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

    /**
     * Emitted to another peer to indicate a board no longer exists and should
     * be deleted. Argument must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardDeleted = "BOARD_DELETED";

    /**
     * Emitted to another peer to indicate an error has occurred.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardError = "BOARD_ERROR";

    /**
     * White board map from board name to board object
     */
    Map<String, Whiteboard> whiteboards;
    public static Map<String, Whiteboard> deletedList = new HashMap<>();


    /**
     * The currently selected white board
     */
    Whiteboard selectedBoard = null;

    /**
     * The peer:port string of the peer. This is synonomous with IP:port,
     * host:port, etc. where it may appear in comments.
     */
    String peerport = "standalone"; // a default value for the non-distributed version
    int whiteboardServerPort = 0;
    String whiteboardServerHost = null;
    PeerManager peerManager;
    String myHostPort;
    public  Map<String, ClientManager>  peerConnectionInfo = new HashMap<>();


    /*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
     */
    JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
    JCheckBox sharedCheckbox;
    DrawArea drawArea;
    JComboBox<String> boardComboBox;
    boolean modifyingComboBox = false;
    boolean modifyingCheckBox = false;

    /**
     * Initialize the white board app.
     */
    public WhiteboardApp(int peerPort, String whiteboardServerHost,
            int whiteboardServerPort) {
        whiteboards = new HashMap<>();
        this.whiteboardServerPort = whiteboardServerPort;
        this.whiteboardServerHost = whiteboardServerHost;


//        boolean ifAttemptToConnectServer = false;
//        Socket attemptSocket;



        peerManager = new PeerManager(peerPort);
//        try {
//            attemptSocket = new Socket(InetAddress.getByName(whiteboardServerHost),whiteboardServerPort);
//            Endpoint endpoint = new Endpoint(attemptSocket, peerManager);
////            endpoint.start();
//            ifAttemptToConnectServer = true;
////            endpoint.interrupt();
//        } catch (UnknownHostException e) {
//        } catch (IOException e) {
//            ifAttemptToConnectServer = false;
//        }


//        if (!ifAttemptToConnectServer) {
//            show(peerport);
//        } else {
        peerport = whiteboardServerHost + ":" + peerPort;
        show(peerport);

        for (String key : whiteboards.keySet()) {
            myHostPort = getIP(whiteboards.get(key).getName()) + ":" + getPort(whiteboards.get(key).getName());
        }


        Map<String, Endpoint>  peerInformation = new HashMap<>();


        connectToWhiteBoardServer(peerManager, peerPort);

        peerManager.on(PeerManager.peerStarted, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.info("Connection from peer: " + endpoint.getOtherEndpointId());
            peerInformation.put(endpoint.getOtherEndpointId(), endpoint);

            endpoint.on(getBoardData, (args2) -> {
                String RequestedBoard = getBoardName((String) args2[0]);
                log.info("onGetBoard: " + RequestedBoard);
                log.info("onBoardListen: " + RequestedBoard);
                endpoint.emit(boardData, whiteboards.get(RequestedBoard).toString());

            }).on(boardUndoAccepted, (args2) -> {
                String newBoardData = (String) args2[0];
                if (getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()) == getBoardVersion(newBoardData)) {
                    log.info("onBoardUndo" + getBoardName(whiteboards.get(getBoardName(newBoardData)).toString()) + "%" + getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()));
                    whiteboards.get(getBoardName(newBoardData)).undo(whiteboards.get(getBoardName(newBoardData)).getVersion());
                    updateComboBox(false ? getBoardName(newBoardData) : null);
                    for (String key : peerInformation.keySet()) {
                        if (!key.equals(endpoint.getOtherEndpointId())) {
                            peerInformation.get(key).emit(boardUndoUpdate, newBoardData);
                        }
                    }
                }

            }).on(boardClearAccepted, (args2) -> {
                String newBoardData = (String) args2[0];
                if (getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()) == getBoardVersion(newBoardData)) {
                    log.info("Clear board: " + getBoardName(newBoardData));
                    whiteboards.get(getBoardName(newBoardData)).clear(whiteboards.get(getBoardName(newBoardData)).getVersion());
                    updateComboBox(false ? getBoardName(newBoardData) : null);
                    for (String key : peerInformation.keySet()) {
                        if (!key.equals(endpoint.getOtherEndpointId())) {
                            peerInformation.get(key).emit(boardClearUpdate,newBoardData);
                        }
                    }
                }

            }).on(boardPathAccepted, (args2) -> {
                String newBoardData = (String) args2[0];
                WhiteboardPath newWhiteboardPath = new WhiteboardPath(getBoardNewPath(newBoardData));
                if (getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()) != getBoardVersion(newBoardData)) {
                    log.info("onBoardPath: " + getBoardNewPath(newBoardData));
                    whiteboards.get(getBoardName(newBoardData)).addPath(newWhiteboardPath, whiteboards.get(getBoardName(newBoardData)).getVersion());
                    updateComboBox(false ? getBoardName(newBoardData) : null);

                    for (String key : peerInformation.keySet()) {
                        if (!key.equals(endpoint.getOtherEndpointId()) ) {
                            peerInformation.get(key).emit(boardPathUpdate, newBoardData);
                        }
                    }
                }

            }).on(boardDeleted, (args2) -> {
                String newBoardData = (String) args2[0];
                if (whiteboards.containsKey(getBoardName(newBoardData))) {
                    log.info("Delete board: " + getBoardName(newBoardData));
                    whiteboards.remove(getBoardName(newBoardData));
                    updateComboBox(false ? getBoardName(newBoardData) : null);
                }
                for (String key : peerInformation.keySet()) {
                    if (!key.equals(endpoint.getOtherEndpointId()) ) {
                        peerInformation.get(key).emit(boardDeleted, newBoardData);
                    }
                }
            });

            undoBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    log.info("onBoardUndo" + getBoardName(selectedBoard.toString()) + "%" + getBoardVersion(selectedBoard.toString()));
                    endpoint.emit(boardUndoUpdate, selectedBoard.toString());
                }
            });

            clearBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    log.info("Clear Board: " + getBoardName(selectedBoard.toString()));
                    endpoint.emit(boardClearUpdate, selectedBoard.toString());
                }
            });

            drawArea.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    if (selectedBoard.isShared()) {
                        endpoint.emit(boardPathUpdate, selectedBoard.toString());
                    }
                }
            });

            deleteBoardBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    log.info("Delete Board: " + getBoardName(selectedBoard.toString()));
                    endpoint.emit(boardDeleted, selectedBoard.toString());
                }
            });

            sharedCheckbox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (!modifyingCheckBox) {
                        setShare(e.getStateChange() == 1);
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            endpoint.emit(boardData, selectedBoard.toString());
                        }
//                        if (e.getStateChange() == ItemEvent.DESELECTED) {
//                            log.info("我取消share给已经连接的peer");
//                            endpoint.emit(WhiteboardServer.unshareBoard, selectedBoard.getName());
//                        }
                    }
                }
            });

        }).on(PeerManager.peerStopped, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.info("peer connection stopped.");
            log.info("Disconnected from peer: " + endpoint.getOtherEndpointId());
        }).on(PeerManager.peerError, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.info("There is an error from: " + endpoint.getOtherEndpointId());
        }).on(PeerManager.peerServerManager, (args) -> {

        }).on(IOThread.ioThread,(args) -> {
            String peerport = (String) args[0];
            // we don't need this info, but let's log it
            log.info("using Internet address: " + peerport);
        });

        peerManager.start();
        peerManager.joinWithClientManagers();


    }

    public void connectToWhiteBoardServer(PeerManager peerManager, int peerport) {
        try {
            ClientManager clientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
            clientManager.on(PeerManager.peerStarted, (args) -> {
                Endpoint endpoint = (Endpoint) args[0];
                log.info("Connecting to whiteboard server");
                // Emitting information to the whiteboard server when I modify the shared button.

                sharedCheckbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        if (!modifyingCheckBox) {
                            setShare(e.getStateChange() == 1);
                            if (e.getStateChange() == ItemEvent.SELECTED) {
                                endpoint.emit(WhiteboardServer.shareBoard, selectedBoard.getName());
                            }
                            if (e.getStateChange() == ItemEvent.DESELECTED) {
                                endpoint.emit(WhiteboardServer.unshareBoard, selectedBoard.getName());
                            }
                        }
                    }
                });

                // Listen for the information of sharing board from the whiteboard server.
                endpoint.on(WhiteboardServer.sharingBoard, (args2) -> {
                    String sharingBoardName = (String) args2[0];
                    log.info("onSharingBoard: " + sharingBoardName);
                    String connectPeerInformation = getIP(sharingBoardName) + ":" + getPort(sharingBoardName);


                    if (peerConnectionInfo.containsKey(connectPeerInformation + myHostPort) || peerConnectionInfo.containsKey(myHostPort + connectPeerInformation)) {

                    } else {
                        getBoardFromPeer(peerManager, sharingBoardName);
                    }

                }).on(WhiteboardServer.unsharingBoard, (args2) -> {
                    String sharingBoardName = (String) args2[0];
                    log.info("unSharingBoard: " + sharingBoardName);
                    whiteboards.remove(sharingBoardName);
                    updateComboBox(false ? sharingBoardName : null);

                }).on(WhiteboardServer.disconnectPeer, (args2) -> {
                    String disconnectBoardName = (String) args2[0];
                    String disconnectPeerName = getIP(disconnectBoardName) + ":" + getPort(disconnectBoardName);

                    whiteboards.remove(disconnectBoardName);
                    updateComboBox(false ? disconnectBoardName : null);

                    if (peerConnectionInfo.containsKey(disconnectPeerName + myHostPort)) {
                        peerConnectionInfo.get(disconnectPeerName + myHostPort).shutdown();
                        peerConnectionInfo.remove(disconnectPeerName + myHostPort);
                    }

                });

            }).on(PeerManager.peerStopped, (args) -> {
                Endpoint endpoint = (Endpoint) args[0];
                log.info("Disconnected from white board server.");
            }).on(PeerManager.peerError, (args) -> {
                Endpoint endpoint = (Endpoint) args[0];
                log.info("Error from white board server.");
            }).on(PeerManager.peerServerManager, (args) -> {

            }).on(IOThread.ioThread, (args) -> {

            });

            clientManager.start();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

    }

    public void getBoardFromPeer(PeerManager peerManager, String sharingBoardName) {
        ClientManager clientManager = null;


        try {
            clientManager = peerManager.connect(getPort(sharingBoardName), getIP(sharingBoardName));
            String connectPeerInformation = getIP(sharingBoardName) + ":" + getPort(sharingBoardName);
            peerConnectionInfo.put(connectPeerInformation + myHostPort, clientManager);
            clientManager.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }



        clientManager.on(PeerManager.peerStarted, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];

            if (!whiteboards.containsKey(sharingBoardName)) {
                endpoint.emit(getBoardData, sharingBoardName);
            }

            endpoint.on(boardData, (args2) -> {
                String boardData = (String) args2[0];
                log.info("onBoardData: " + boardData);
                Whiteboard newWhiteBoard = new Whiteboard(getBoardName(boardData), true);
                newWhiteBoard.whiteboardFromString(getBoardName(boardData), getBoardData(boardData));
                newWhiteBoard.setShared(true);
                addBoard(newWhiteBoard, false);
            }).on(boardUndoUpdate, (args2) -> {
                String newBoardData = (String) args2[0];

                if (getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()) == getBoardVersion(newBoardData)) {
                    log.info("onBoardUndo" + getBoardName(whiteboards.get(getBoardName(newBoardData)).toString()) + "%" + getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()));
                    whiteboards.get(getBoardName(newBoardData)).undo(whiteboards.get(getBoardName(newBoardData)).getVersion());
                    updateComboBox(false ? getBoardName(newBoardData) : null);
                }

            }).on(boardPathUpdate, (args2) -> {
                String newBoardData = (String) args2[0];
                WhiteboardPath newWhiteboardPath = new WhiteboardPath(getBoardNewPath(newBoardData));

                if (whiteboards.containsKey(getBoardName(newBoardData))) {
                    if (getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()) != getBoardVersion(newBoardData)) {
                        log.info("onBoardPath" + getBoardNewPath(newBoardData));
                        whiteboards.get(getBoardName(newBoardData)).addPath(newWhiteboardPath, whiteboards.get(getBoardName(newBoardData)).getVersion());
                        updateComboBox(false ? getBoardName(newBoardData) : null);
                    }
                }


            }).on(boardClearUpdate, (args2) -> {
                String newBoardData = (String) args2[0];
                if (getBoardVersion(whiteboards.get(getBoardName(newBoardData)).toString()) == getBoardVersion(newBoardData)) {
                    log.info("Clear board: " + getBoardName(newBoardData));
                    whiteboards.get(getBoardName(newBoardData)).clear(whiteboards.get(getBoardName(newBoardData)).getVersion());
                    updateComboBox(false ? getBoardName(newBoardData) : null);
                }
            }).on(boardDeleted, (args2) -> {
                String newBoardData = (String) args2[0];
                if (whiteboards.containsKey(getBoardName(newBoardData))) {
                    log.info("Delete board: " + getBoardName(newBoardData));
                    whiteboards.remove(getBoardName(newBoardData));
                    updateComboBox(false ? getBoardName(newBoardData) : null);
                }


            });

            undoBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    log.info("onBoardUndo" + getBoardName(selectedBoard.toString()) + "%" + getBoardVersion(selectedBoard.toString()));
                    endpoint.emit(boardUndoAccepted, selectedBoard.toString());
                }
            });

            clearBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    log.info("Clear Board: " + getBoardName(selectedBoard.toString()));
                    endpoint.emit(boardClearAccepted, selectedBoard.toString());
                }
            });

            deleteBoardBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    log.info("Delete Board: " + getBoardName(selectedBoard.toString()));
                    endpoint.emit(boardDeleted, selectedBoard.toString());
                }
            });

            drawArea.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                if (selectedBoard.isShared()) {
                    endpoint.emit(boardPathAccepted, selectedBoard.toString());
                }
                }
            });


        }).on(peerManager.peerStopped, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
//            log.info("Disconnected from peer: " + endpoint.getOtherEndpointId());
//            log.info("peer connection stopped.");
        });


    }


    /**
     * ****
     *
     * Utility methods to extract fields from argument strings.
     *
     *****
     */
    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return peer:port:boardid
     */
    public static String getBoardName(String data) {
        String[] parts = data.split("%", 2);
        return parts[0];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return boardid%version%PATHS
     */
    public static String getBoardIdAndData(String data) {
        String[] parts = data.split(":");
        return parts[2];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return version%PATHS
     */
    public static String getBoardData(String data) {
        String[] parts = data.split("%", 2);
        return parts[1];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return version
     */
    public static long getBoardVersion(String data) {
        String[] parts = data.split("%", 3);
        return Long.parseLong(parts[1]);
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return PATHS
     */
    public static String getBoardPaths(String data) {
        String[] parts = data.split("%", 3);
        return parts[2];
    }

    public static String getBoardNewPath(String data) {
        String[] paths = data.split("%", 3);
        String[] lastPath = paths[2].split("%");
        return lastPath[lastPath.length - 1];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return peer
     */
    public static String getIP(String data) {
        String[] parts = data.split(":");
        return parts[0];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return port
     */
    public static int getPort(String data) {
        String[] parts = data.split(":");
        return Integer.parseInt(parts[1]);
    }

    /**
     * ****
     *
     * Methods called from events.
     *
     *****
     */
    // From whiteboard server
    // From whiteboard peer
    /**
     * ****
     *
     * Methods to manipulate data locally. Distributed systems related code has
     * been cut from these methods.
     *
     *****
     */
    /**
     * Wait for the peer manager to finish all threads.
     */
    public void waitToFinish() {

    }

    /**
     * Add a board to the list that the user can select from. If select is true
     * then also select this board.
     *
     * @param whiteboard
     * @param select
     */
    public void addBoard(Whiteboard whiteboard, boolean select) {
        synchronized (whiteboards) {
            whiteboards.put(whiteboard.getName(), whiteboard);
        }
        updateComboBox(select ? whiteboard.getName() : null);
    }

    /**
     * Delete a board from the list.
     *
     * @param boardname must have the form peer:port:boardid
     */
    public void deleteBoard(String boardname) {
        synchronized (whiteboards) {
            Whiteboard whiteboard = whiteboards.get(boardname);
            if (whiteboard != null) {
                whiteboards.remove(boardname);
            }
        }
        updateComboBox(null);
    }

    /**
     * Create a new local board with name peer:port:boardid. The boardid
     * includes the time stamp that the board was created at.
     */
    public void createBoard() {
        String name = peerport + ":board" + Instant.now().toEpochMilli();
        Whiteboard whiteboard = new Whiteboard(name, false);
        addBoard(whiteboard, true);
    }

    /**
     * Add a path to the selected board. The path has already been drawn on the
     * draw area; so if it can't be accepted then the board needs to be redrawn
     * without it.
     *
     * @param currentPath
     */
    public void pathCreatedLocally(WhiteboardPath currentPath) {
        if (selectedBoard != null) {
            if (!selectedBoard.addPath(currentPath, selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard(); // just redraw the screen without the path

            } else {
                // was accepted locally, so do remote stuff if needed
            }
        } else {
            log.severe("path created without a selected board: " + currentPath);
        }
    }

    /**
     * Clear the selected whiteboard.
     */
    public void clearedLocally() {
        if (selectedBoard != null) {
            if (!selectedBoard.clear(selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard();
            } else {
                // was accepted locally, so do remote stuff if needed

                drawSelectedWhiteboard();
            }
        } else {
            log.severe("cleared without a selected board");
        }
    }

    /**
     * Undo the last path of the selected whiteboard.
     */
    public void undoLocally() {
        if (selectedBoard != null) {
            if (!selectedBoard.undo(selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard();
            } else {

                drawSelectedWhiteboard();
            }
        } else {
            log.severe("undo without a selected board");
        }
    }

    /**
     * The variable selectedBoard has been set.
     */
    public void selectedABoard() {
        drawSelectedWhiteboard();
        log.info("selected board: " + selectedBoard.getName());
    }

//    public void onSharingBoard() {
//
//    }
    /**
     * Set the share status on the selected board.
     */
    public void setShare(boolean share) {
        if (selectedBoard != null) {
            selectedBoard.setShared(share);
        } else {
            log.severe("there is no selected board");
        }
    }

    public static Map getDeletedList() {
        return deletedList;
    }

    /**
     * Called by the gui when the user closes the app.
     */
    public void guiShutdown() {
        // do some final cleanup
        HashSet<Whiteboard> existingBoards = new HashSet<>(whiteboards.values());


//        for (String key2 : whiteboards.keySet()) {
//            deletedList.put(key2, whiteboards.get(key2));
//        }


        existingBoards.forEach((board) -> {
            deleteBoard(board.getName());
        });

//        for (String key2 : deletedList.keySet()) {
//            log.info("跑到这了吗2");
//            log.info(deletedList.get(key2).toString());
//        }


        try {
            peerManager.shutdown();
        } catch (Exception e) {
        }


        whiteboards.values().forEach((whiteboard) -> {

        });
    }

    /**
     * ****
     *
     * GUI methods and callbacks from GUI for user actions. You probably do not
     * need to modify anything below here.
     *
     *****
     */
    /**
     * Redraw the screen with the selected board
     */
    public void drawSelectedWhiteboard() {
        drawArea.clear();
        if (selectedBoard != null) {
            selectedBoard.draw(drawArea);
        }
    }

    /**
     * Setup the Swing components and start the Swing thread, given the peer's
     * specific information, i.e. peer:port string.
     */
    public void show(String peerport) {
        // create main frame
        JFrame frame = new JFrame("Whiteboard Peer: " + peerport);
        Container content = frame.getContentPane();
        // set layout on content pane
        content.setLayout(new BorderLayout());
        // create draw area
        drawArea = new DrawArea(this);

        // add to content pane
        content.add(drawArea, BorderLayout.CENTER);

        // create controls to apply colors and call clear feature
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        /**
         * Action listener is called by the GUI thread.
         */
        ActionListener actionListener = new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == clearBtn) {
                    clearedLocally();
                } else if (e.getSource() == blackBtn) {
                    drawArea.setColor(Color.black);
                } else if (e.getSource() == redBtn) {
                    drawArea.setColor(Color.red);
                } else if (e.getSource() == boardComboBox) {
                    if (modifyingComboBox) {
                        return;
                    }
                    if (boardComboBox.getSelectedIndex() == -1) {
                        return;
                    }
                    String selectedBoardName = (String) boardComboBox.getSelectedItem();
                    if (whiteboards.get(selectedBoardName) == null) {
                        log.severe("selected a board that does not exist: " + selectedBoardName);
                        return;
                    }
                    selectedBoard = whiteboards.get(selectedBoardName);
                    // remote boards can't have their shared status modified
                    if (selectedBoard.isRemote()) {
                        sharedCheckbox.setEnabled(false);
                        sharedCheckbox.setVisible(false);
                    } else {
                        modifyingCheckBox = true;
                        sharedCheckbox.setSelected(selectedBoard.isShared());
                        modifyingCheckBox = false;
                        sharedCheckbox.setEnabled(true);
                        sharedCheckbox.setVisible(true);
                    }
                    selectedABoard();
                } else if (e.getSource() == createBoardBtn) {
                    createBoard();
                } else if (e.getSource() == undoBtn) {
                    if (selectedBoard == null) {
                        log.severe("there is no selected board to undo");
                        return;
                    }
                    undoLocally();
                } else if (e.getSource() == deleteBoardBtn) {
                    if (selectedBoard == null) {
                        log.severe("there is no selected board to delete");
                        return;
                    }
                    deleteBoard(selectedBoard.getName());
                }
            }
        };

        clearBtn = new JButton("Clear Board");
        clearBtn.addActionListener(actionListener);
        clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
        clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        blackBtn = new JButton("Black");
        blackBtn.addActionListener(actionListener);
        blackBtn.setToolTipText("Draw with black pen");
        blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        redBtn = new JButton("Red");
        redBtn.addActionListener(actionListener);
        redBtn.setToolTipText("Draw with red pen");
        redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteBoardBtn = new JButton("Delete Board");
        deleteBoardBtn.addActionListener(actionListener);
        deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
        deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        createBoardBtn = new JButton("New Board");
        createBoardBtn.addActionListener(actionListener);
        createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
        createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        undoBtn = new JButton("Undo");
        undoBtn.addActionListener(actionListener);
        undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
        undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        sharedCheckbox = new JCheckBox("Shared");
        sharedCheckbox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (!modifyingCheckBox) {
                    setShare(e.getStateChange() == 1);
                }
//                if (e.getStateChange() == ItemEvent.SELECTED) {
//                    log.info("打钩变化的方法");
//                    
//                }
            }
        });
        sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
        sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);

        // create a drop list for boards to select from
        JPanel controlsNorth = new JPanel();
        boardComboBox = new JComboBox<String>();
        boardComboBox.addActionListener(actionListener);

        // add to panel
        controlsNorth.add(boardComboBox);
        controls.add(sharedCheckbox);
        controls.add(createBoardBtn);
        controls.add(deleteBoardBtn);
        controls.add(blackBtn);
        controls.add(redBtn);
        controls.add(undoBtn);
        controls.add(clearBtn);

        // add to content pane
        content.add(controls, BorderLayout.WEST);
        content.add(controlsNorth, BorderLayout.NORTH);

        frame.setSize(600, 600);

        // create an initial board
        createBoard();

        // closing the application
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(frame,
                        "Are you sure you want to close this window?", "Close Window?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    guiShutdown();
                    frame.dispose();
                }
            }
        });

        // show the swing paint result
        frame.setVisible(true);

    }

    /**
     * Update the GUI's list of boards. Note that this method needs to update
     * data that the GUI is using, which should only be done on the GUI's
     * thread, which is why invoke later is used.
     *
     * @param select, board to select when list is modified or null for default
     * selection
     */
    private void updateComboBox(String select) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                modifyingComboBox = true;
                boardComboBox.removeAllItems();
                int anIndex = -1;
                synchronized (whiteboards) {
                    ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
                    Collections.sort(boards);
                    for (int i = 0; i < boards.size(); i++) {
                        String boardname = boards.get(i);
                        boardComboBox.addItem(boardname);
                        if (select != null && select.equals(boardname)) {
                            anIndex = i;
                        } else if (anIndex == -1 && selectedBoard != null
                                && selectedBoard.getName().equals(boardname)) {
                            anIndex = i;
                        }
                    }
                }
                modifyingComboBox = false;
                if (anIndex != -1) {
                    boardComboBox.setSelectedIndex(anIndex);
                } else {
                    if (whiteboards.size() > 0) {
                        boardComboBox.setSelectedIndex(0);
                    } else {
                        drawArea.clear();
                        createBoard();
                    }
                }

            }
        });
    }

}
