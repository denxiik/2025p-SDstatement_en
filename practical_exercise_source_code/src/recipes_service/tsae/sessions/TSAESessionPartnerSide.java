/*
 * Copyright (c) Joan-Manuel Marques 2013. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of the practical assignment of Distributed Systems course.
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this code.  If not, see <http://www.gnu.org/licenses/>.
 */

package recipes_service.tsae.sessions;

import java.io.IOException;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import communication.ObjectInputStream_DS;
import communication.ObjectOutputStream_DS;
import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import recipes_service.ServerData;
import recipes_service.communication.Message;
import recipes_service.communication.MessageAErequest;
import recipes_service.communication.MessageEndTSAE;
import recipes_service.communication.MessageOperation;
import recipes_service.communication.MsgType;
import recipes_service.data.Operation;
import recipes_service.tsae.data_structures.TimestampMatrix; // Keep import for now, but will remove usage
import recipes_service.tsae.data_structures.TimestampVector;

import lsim.library.api.LSimLogger;

/**
 * @author Joan-Manuel Marques
 * December 2012
 *
 */
public class TSAESessionPartnerSide extends Thread{

    private Socket socket = null;
    private ServerData serverData = null;

    public TSAESessionPartnerSide(Socket socket, ServerData serverData) {
        super("TSAEPartnerSideThread");
        this.socket = socket;
        this.serverData = serverData;
    }

    public void run() {

        Message msg = null;
        int current_session_number = -1;
        ObjectOutputStream_DS out = null;
        ObjectInputStream_DS in = null;

        try {
            out = new ObjectOutputStream_DS(socket.getOutputStream());
            in = new ObjectInputStream_DS(socket.getInputStream());

            // receive originator's summary (and dummy ack)
            msg = (Message) in.readObject();
            current_session_number = msg.getSessionNumber(); // Get session number early
            LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] TSAE session");
            LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] received message: " + msg);

            if (msg.type() == MsgType.AE_REQUEST) {
                MessageAErequest request = (MessageAErequest) msg;
                TimestampVector originatorSummary = request.getSummary();
                // TimestampMatrix originatorAck = request.getAck(); // Not used in protocol without acks

                // Update local state based on originator's summary
                serverData.getSummary().updateMax(originatorSummary);
                // serverData.getAck().updateMax(originatorAck); // REMOVED: No ACK update in this version

                // send operations to originator
                List<Operation> operationsToSend = serverData.getLog().listNewer(originatorSummary);
                for (Operation op : operationsToSend) {
                    msg = new MessageOperation(op);
                    msg.setSessionNumber(current_session_number);
                    out.writeObject(msg);
                    LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] sent message: " + msg);
                }

                // send to originator: local's summary (and dummy ack)
                TimestampVector localSummary = serverData.getSummary().clone();
                // If MessageAErequest must pass a TimestampMatrix, use a dummy clone.
                TimestampMatrix dummyLocalAck = serverData.getAck().clone(); // Keep for compilation, but not used in logic

                msg = new MessageAErequest(localSummary, dummyLocalAck); // Using existing constructor
                msg.setSessionNumber(current_session_number);
                out.writeObject(msg);
                LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] sent message: " + msg);

                // receive operations from originator
                msg = (Message) in.readObject();
                LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] received message: " + msg);
                while (msg.type() == MsgType.OPERATION) {
                    MessageOperation operationMessage = (MessageOperation) msg;
                    serverData.getLog().add(operationMessage.getOperation());
                    serverData.getSummary().updateTimestamp(operationMessage.getOperation().getTimestamp());
                    msg = (Message) in.readObject();
                    LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] received message: " + msg);
                }

                // receive message to inform about the ending of the TSAE session
                if (msg.type() == MsgType.END_TSAE) {
                    // send and "end of TSAE session" message
                    msg = new MessageEndTSAE();
                    msg.setSessionNumber(current_session_number);
                    out.writeObject(msg);
                    LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] sent message: " + msg);
                }
            }
        } catch (ClassNotFoundException e) {
            LSimLogger.log(Level.FATAL, "[TSAESessionPartnerSide] [session: " + current_session_number + "] ClassNotFoundException: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            LSimLogger.log(Level.ERROR, "[TSAESessionPartnerSide] [session: " + current_session_number + "] IOException during session: " + e.getMessage());
            // No System.exit(1) here as an IOException might just mean a temporary network issue.
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                LSimLogger.log(Level.ERROR, "[TSAESessionPartnerSide] [session: " + current_session_number + "] Error closing resources: " + e.getMessage());
            }
        }

        LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: " + current_session_number + "] End TSAE session");
    }
}