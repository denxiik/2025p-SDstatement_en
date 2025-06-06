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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import lsim.library.api.LSimLogger;

/**
 * @author Joan-Manuel Marques
 * December 2012
 *
 */
public class TimestampVector implements Serializable{
	// Only for the zip file with the correct solution of phase1.Needed for the logging system for the phase1. sgeag_2018p 
//	private transient LSimCoordinator lsim = LSimFactory.getCoordinatorInstance();
	// Needed for the logging system sgeag@2017
//	private transient LSimWorker lsim = LSimFactory.getWorkerInstance();
	
	private static final long serialVersionUID = -765026247959198886L;
	/**
	 * This class stores a summary of the timestamps seen by a node.
	 * For each node, stores the timestamp of the last received operation.
	 */
	
	private ConcurrentHashMap<String, Timestamp> timestampVector= new ConcurrentHashMap<String, Timestamp>();
	
	public TimestampVector (List<String> participants){
		// create and empty TimestampVector
		for (Iterator<String> it = participants.iterator(); it.hasNext(); ){
			String id = it.next();
			// when sequence number of timestamp < 0 it means that the timestamp is the null timestamp
			timestampVector.put(id, new Timestamp(id, Timestamp.NULL_TIMESTAMP_SEQ_NUMBER));
		}
	}

	/**
	 * Updates the timestamp vector with a new timestamp. 
	 * @param timestamp
	 */
	public void updateTimestamp(Timestamp timestamp){
		LSimLogger.log(Level.TRACE, "Updating the TimestampVectorInserting with the timestamp: "+timestamp);
        timestampVector.put(timestamp.getHostid(),timestamp);
	}
	
	/**
     * merge in another vector, taking the element wise maximum
     *
     * @param other (a timestamp vector)
     */
    public synchronized void updateMax(TimestampVector other) {
        if (other == null) {
            return;
        }
        /**
         * Goes through all the nodes in the currect vector and looks for the same node
         * in the other vector. If one exists and it's timestamp is higher than the current,
         * the current timestamp gets replaced.
         */

        for (String node : this.timestampVector.keySet()) {
            Timestamp otherTimestamp = other.getLast(node);

            if (otherTimestamp == null) {
                continue;
            } else if (this.getLast(node).compare(otherTimestamp) < 0) {
                this.timestampVector.replace(node, otherTimestamp);
            }
        }

    }
	
	/**
	 * 
	 * @param node
	 * @return the last timestamp issued by node that has been
	 * received.
	 */
	public Timestamp getLast(String node){
		return timestampVector.get(node);
	}
	
	/**
	 * merges local timestamp vector with tsVector timestamp vector taking
	 * the smallest timestamp for each node.
	 * After merging, local node will have the smallest timestamp for each node.
	 *  @param tsVector (timestamp vector)
	 */
	public synchronized void mergeMin(TimestampVector other) {
        if (other == null) {
            return;
        }
        /**
         * Similar to update max with the difference that I also add new entries if they
         * don't yet exist in the current timestamp vector. This is due to the way,
         * this function is used by TimetampMatrix.
         */

        for (Map.Entry<String, Timestamp> entry : other.timestampVector.entrySet()) {
            String node = entry.getKey();
            Timestamp otherTimestamp = entry.getValue();
            Timestamp thisTimestamp = this.getLast(node);
            
            if (thisTimestamp == null) {
                this.timestampVector.put(node, otherTimestamp);
            } else if (otherTimestamp.compare(thisTimestamp) < 0) {
                this.timestampVector.replace(node, otherTimestamp);
            }
        }

    }
	
	/**
	 * clone
	 */
	public TimestampVector clone(){
		TimestampVector clone = new TimestampVector(new ArrayList<>(timestampVector.keySet()));
        for (String hostId : timestampVector.keySet()) {
            clone.timestampVector.put(hostId, timestampVector.get(hostId));
        }
        return clone;
	}
	
	/**
	 * equals
	 */
	public boolean equals(Object obj){
		if (this == obj)
            return true;
		if (obj == null)
            return false;
		if (getClass() != obj.getClass())
            return false;
		
		TimestampVector other = (TimestampVector) obj;
		return timestampVector.equals(other.timestampVector);
	}

	/**
	 * toString
	 */
	@Override
	public synchronized String toString() {
		String all="";
		if(timestampVector==null){
			return all;
		}
		for(Enumeration<String> en=timestampVector.keys(); en.hasMoreElements();){
			String name=en.nextElement();
			if(timestampVector.get(name)!=null)
				all+=timestampVector.get(name)+"\n";
		}
		return all;
	}
}
