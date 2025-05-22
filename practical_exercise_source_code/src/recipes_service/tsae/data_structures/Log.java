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
package recipes_service.tsae.data_structures;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import recipes_service.data.Operation;


/**
 * @author Joan-Manuel Marques December 2012
 *
 */
public class Log implements Serializable {

    private static final long serialVersionUID = -4864990265268259700L;
    /**
     * This class implements a log, that stores the operations received by a
     * client. They are stored in a ConcurrentHashMap (a hash table), that
     * stores a list of operations for each member of the group.
     */
    private ConcurrentHashMap<String, List<Operation>> log = new ConcurrentHashMap<>();

    public Log(List<String> participants) {
        // create an empty log
        for (String participant : participants) {
            log.put(participant, new Vector<Operation>());
        }
    }

    /**
     * inserts an operation into the log. Operations are inserted in order. If
     * the last operation for the user is not the previous operation than the
     * one being inserted, the insertion will fail.
     *
     * @param op
     * @return true if op is inserted, false otherwise.
     */
    public synchronized boolean add(Operation op) {
        String hostId = op.getTimestamp().getHostid();
        Timestamp lastTimestamp = this.getLastTimestamp(hostId);
        long timestampDifference = op.getTimestamp().compare(lastTimestamp);

        /**
         * Check if the inserted Operation is the next to follow.
         * If yes, insert it and return true so it can be purged eventually later,
         * otherwise return false so that it is kept for later.
         */
        
        if ((lastTimestamp == null && timestampDifference == 0)
                || (lastTimestamp != null && timestampDifference == 1)) {
            this.log.get(hostId).add(op);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks the received summary (sum) and determines the operations contained
     * in the log that have not been seen by the proprietary of the summary.
     * Returns them in an ordered list.
     *
     * @param sum
     * @return list of operations
     */
    public synchronized List<Operation> listNewer(TimestampVector summary) {
        List<Operation> missingList = new Vector();

        /**
         * Go through all the hosts in the log
         */

        for (String node : this.log.keySet()) {
            List<Operation> operations = this.log.get(node);
            Timestamp timestampToCompare = summary.getLast(node);

        	/**
        	 * Go through all the operations per host and collect all those which are smaller
        	 * than the timestampVector passed formthe specific host.
        	 */
            for (Operation op : operations) {
                if (op.getTimestamp().compare(timestampToCompare) > 0) {
                    missingList.add(op);
                }
            }
        }
        return missingList;
    }

    /**
     * Removes from the log the operations that have been acknowledged by all
     * the members of the group, according to the provided ackSummary.
     *
     * @param ack: ackSummary.
     */
    public synchronized void purgeLog(TimestampMatrix ack) {
        /**
         * Create a minTimestampVector from the matrix. Only logs older than this will be purged later.
         * minTimestampVector guarantees that all clients have received the information up to that timestamp.
         */
        TimestampVector minTimestampVector = ack.minTimestampVector();

//        StringBuilder sb = new StringBuilder("Log - PurgeLog... Ack-Matrix: ");
//        sb.append(ack);
//        sb.append(" - Min-TimestampVector: ");
//        sb.append(minTimestampVector);
//        sb.append(" - Log Before purge: ");
//        sb.append(this);
        
        /**
         * Go through all entries in the log map.
         */
        for (Map.Entry<String, List<Operation>> entry : log.entrySet()) {
            String participant = entry.getKey();
            List<Operation> operations = entry.getValue();
            Timestamp lastTimestamp = minTimestampVector.getLast(participant);
            /**
             * Take the last timestamp for the node/host/participant.
             * If there is none, ignore it and continue with the loop
             */
            if (lastTimestamp == null) {
                continue;
            }

            /**
             * Go from back to front through all the operations and delete those, 
             * which are older than the latest received Timestamp.
             */
            for (int i = operations.size() - 1; i >= 0; i--) {
                Operation op = operations.get(i);

                if (op.getTimestamp().compare(lastTimestamp) < 0) {
                    operations.remove(i);
                }
            }
        }
        
//        sb.append(" - Log After purge: ");
//        sb.append(this);
//        System.out.println(sb);
    }

    /**
     * equals
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (this == obj) {
            return true;
        } else if (!(obj instanceof Log)) {
            return false;
        }

        Log other = (Log) obj;

        if (this.log == other.log) {
            return true;
        } else if (this.log == null || other.log == null) {
            return false;
        } else {
            return this.log.equals(other.log);
        }
    }

    /**
     * toString
     */
    @Override
    public synchronized String toString() {
        String name = "";
        for (List<Operation> sublog : log.values()) {
            for (Operation entry : sublog) {
                name += entry.toString() + "\n";
            }
        }

        return name;
    }

    private Timestamp getLastTimestamp(String hostId) {
        List<Operation> operations = this.log.get(hostId);

        if (operations == null || operations.isEmpty()) {
            return null;
        } else {
            return operations.get(operations.size() - 1).getTimestamp();
        }
    }
}
