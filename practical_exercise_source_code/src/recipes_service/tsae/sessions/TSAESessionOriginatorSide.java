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
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import recipes_service.ServerData;
import recipes_service.activity_simulation.SimulationData;
import recipes_service.communication.Host;
import recipes_service.communication.Message;
import recipes_service.communication.MessageAErequest;
import recipes_service.communication.MessageEndTSAE;
import recipes_service.communication.MessageOperation;
import recipes_service.communication.MsgType;
import recipes_service.data.Operation;
import recipes_service.tsae.data_structures.TimestampMatrix; // Keep import for now, but will remove usage
import recipes_service.tsae.data_structures.TimestampVector;
import communication.ObjectInputStream_DS;
import communication.ObjectOutputStream_DS;
import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import lsim.library.api.LSimLogger;

/**
 * @author Joan-Manuel Marques, Daniel Lázaro Iglesias
 * December 2012
 *
 */
public class TSAESessionOriginatorSide extends TimerTask{
    private static AtomicInteger session_number = new AtomicInteger(0);

    private ServerData serverData;
    public TSAESessionOriginatorSide(ServerData serverData){
        super();
        this.serverData=serverData;
    }

    /**
     * Implementation of the TimeStamped Anti-Entropy protocol
     */
    public void run() {
        sessionWithN(serverData.getNumberSessions());
    }

    /**
     * This method performs num TSAE sessions with num random servers
     * * @param num
     */
    public void sessionWithN(int num) {
        if (!SimulationData.getInstance().isConnected())
            return;
        List<Host> partnersTSAEsession = serverData.getRandomPartners(num);
        Host n;
        for (int i = 0; i < partnersTSAEsession.size(); i++) {
            n = partnersTSAEsession.get(i);
            sessionTSAE(n);
        }
    }

    /**
     * This method perform a TSAE session with the partner server n
     * * @param n
     */
    private void sessionTSAE(Host n) {
        int current_session_number = session_number.incrementAndGet();
        if (n == null)
            return;

        LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] TSAE session");

        Socket socket = null;
        ObjectInputStream_DS in = null;
        ObjectOutputStream_DS out = null;

        try {
            socket = new Socket(n.getAddress(), n.getPort());
            out = new ObjectOutputStream_DS(socket.getOutputStream());
            in = new ObjectInputStream_DS(socket.getInputStream());

            // Get a temporary copy of the local summary (without ACKs)
            TimestampVector localSummary = serverData.getSummary().clone();
            // In TSAE without ACKs, the ack matrix is not exchanged.
            // However, the MessageAErequest constructor still requires a TimestampMatrix.
            // We'll pass a dummy or empty one, or adapt MessageAErequest if possible.
            // For now, let's pass a clone of the local ack, but its content related to 'ack' won't be used in the logic.
            // If the MessageAErequest class is truly to be 'without acks', its constructor would change.
            // Assuming the message format remains, but the 'ack' content is ignored.
            TimestampMatrix dummyLocalAck = serverData.getAck().clone(); // Keep for compilation, but not used in logic

            // Send to partner: local's summary (and a dummy ack)
            Message msg = new MessageAErequest(localSummary, dummyLocalAck); // Using existing constructor
            msg.setSessionNumber(current_session_number);
            out.writeObject(msg);
            LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] sent message: " + msg);

            // receive operations from partner
            msg = (Message) in.readObject();
            LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] received message: " + msg);
            while (msg.type() == MsgType.OPERATION) {
                MessageOperation operationMessage = (MessageOperation) msg;
                serverData.getLog().add(operationMessage.getOperation());
                serverData.getSummary().updateTimestamp(operationMessage.getOperation().getTimestamp());
                msg = (Message) in.readObject();
                LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] received message: " + msg);
            }

            // receive partner's summary (and dummy ack)
            if (msg.type() == MsgType.AE_REQUEST) {
                MessageAErequest request = (MessageAErequest) msg;
                TimestampVector partnerSummary = request.getSummary();
                // TimestampMatrix partnerAck = request.getAck(); // Not used in protocol without acks

                serverData.getSummary().updateMax(partnerSummary);
                // serverData.getAck().updateMax(partnerAck); // REMOVED: No ACK update in this version

                // send operations
                List<Operation> operationsToSend = serverData.getLog().listNewer(partnerSummary);
                for (Operation op : operationsToSend) {
                    msg = new MessageOperation(op);
                    msg.setSessionNumber(current_session_number);
                    out.writeObject(msg);
                    LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] sent message: " + msg);
                }

                // send and "end of TSAE session" message
                msg = new MessageEndTSAE();
                msg.setSessionNumber(current_session_number);
                out.writeObject(msg);
                LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] sent message: " + msg);

                // receive message to inform about the ending of the TSAE session
                msg = (Message) in.readObject();
                LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] received message: " + msg);
                if (msg.type() == MsgType.END_TSAE) {
                    // Session successfully ended
                }
            }
        } catch (ClassNotFoundException e) {
            LSimLogger.log(Level.FATAL, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] ClassNotFoundException: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            // Log the IOException for better debugging in a real scenario
            LSimLogger.log(Level.ERROR, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] IOException during session: " + e.getMessage());
            // No System.exit(1) here as an IOException might just mean a temporary network issue or partner down,
            // and the TimerTask will try again later.
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                LSimLogger.log(Level.ERROR, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] Error closing resources: " + e.getMessage());
            }
        }

        LSimLogger.log(Level.TRACE, "[TSAESessionOriginatorSide] [session: " + current_session_number + "] End TSAE session");
    }
}