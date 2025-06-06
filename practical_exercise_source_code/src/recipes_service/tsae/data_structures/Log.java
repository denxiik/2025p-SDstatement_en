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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import recipes_service.data.Operation;
//LSim logging system imports sgeag@2017
//import lsim.coordinator.LSimCoordinator;
import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import lsim.library.api.LSimLogger;

/**
 * @author Joan-Manuel Marques, Daniel Lázaro Iglesias
 * December 2012
 *
 */
public class Log implements Serializable{
	// Only for the zip file with the correct solution of phase1.Needed for the logging system for the phase1. sgeag_2018p 
//	private transient LSimCoordinator lsim = LSimFactory.getCoordinatorInstance();
	// Needed for the logging system sgeag@2017
//	private transient LSimWorker lsim = LSimFactory.getWorkerInstance();

	private static final long serialVersionUID = -4864990265268259700L;
	/**
	 * This class implements a log, that stores the operations
	 * received  by a client.
	 * They are stored in a ConcurrentHashMap (a hash table),
	 * that stores a list of operations for each member of 
	 * the group.
	 */
	private ConcurrentHashMap<String, List<Operation>> log= new ConcurrentHashMap<String, List<Operation>>();  

	public Log(List<String> participants){
		// create an empty log
		for (Iterator<String> it = participants.iterator(); it.hasNext(); ){
			log.put(it.next(), new Vector<Operation>());
		}
	}

	/**
	 * inserts an operation into the log. Operations are 
	 * inserted in order. If the last operation for 
	 * the user is not the previous operation than the one 
	 * being inserted, the insertion will fail.
	 * 
	 * @param op
	 * @return true if op is inserted, false otherwise.
	 */
	public boolean add(Operation op){
		String hostId = op.getTimestamp().getHostid();
        List<Operation> ops = log.get(hostId);
        Timestamp last;

        if(!ops.isEmpty()) {
                last = ops.get(ops.size()-1).getTimestamp();
        }else {
                last = null;
        }
        if (last != null && op.getTimestamp().compare(last) <= 0) {
            return false;
        } else {
            ops.add(op);
            return true;
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
	 * Removes from the log the operations that have
	 * been acknowledged by all the members
	 * of the group, according to the provided
	 * ackSummary. 
	 * @param ack: ackSummary.
	 */
    public synchronized void purgeLog(TimestampMatrix ack) {
        /**
         * Create a minTimestampVector from the matrix. Only logs older than this will be purged later.
         * minTimestampVector guarantees that all clients have received the information up to that timestamp.
         */
        TimestampVector minTimestampVector = ack.minTimestampVector();
        
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
    }

	/**
	 * equals
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
            return true;
		if (obj == null)
            return false;
		if (getClass() != obj.getClass())
            return false;
		Log other = (Log) obj;
		return log.equals(other.log);
	}

	/**
	 * toString
	 */
	@Override
	public synchronized String toString() {
		String name="";
		for(Enumeration<List<Operation>> en=log.elements();
		en.hasMoreElements(); ){
		List<Operation> sublog=en.nextElement();
		for(ListIterator<Operation> en2=sublog.listIterator(); en2.hasNext();){
			name+=en2.next().toString()+"\n";
		}
	}
		
		return name;
	}
}
