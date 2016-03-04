/*******************************************************************************
 * oltpbenchmark.com
 *  
 *  Project Info:  http://oltpbenchmark.com
 *  Project Members:  	Carlo Curino <carlo.curino@gmail.com>
 * 				Evan Jones <ej@evanjones.ca>
 * 				DIFALLAH Djellel Eddine <djelleleddine.difallah@unifr.ch>
 * 				Andy Pavlo <pavlo@cs.brown.edu>
 * 				CUDRE-MAUROUX Philippe <philippe.cudre-mauroux@unifr.ch>  
 *  				Yang Zhang <yaaang@gmail.com> 
 * 
 *  This library is free software; you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Foundation;
 *  either version 3.0 of the License, or (at your option) any later version.
 * 
 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 ******************************************************************************/
package com.oltpbenchmark.benchmarks.twitter.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;


public class TwitterGraphLoader {

	String filename;
	BufferedReader br = null;
	Random r = null;
	static final double READ_WRITE_RATIO = 11.8; // from
													// http://www.globule.org/publi/WWADH_comnet2009.html
	int max_user_id = Integer.MAX_VALUE;
	int last_followee = -1;

	public TwitterGraphLoader(String filename) throws FileNotFoundException {
		r = new Random();
		this.filename = filename;

		if(filename==null || filename.isEmpty())
			throw new FileNotFoundException("You must specify a filename to instantiate the TwitterGraphLoader... (probably missing in your workload configuration?)");

		File file = new File(filename);
		FileReader fr = new FileReader(file);
		br = new BufferedReader(fr);
	}

	public void setMaxUserId(int max_user_id) {
		this.max_user_id = max_user_id;
	}
	
	// Important: assumes that file is sorted by followee, follower
	public TwitterGraphEdge readNextEdge() throws IOException {
		int followee = Integer.MAX_VALUE;
		int follower = Integer.MAX_VALUE;

		while(follower > max_user_id) {
			String line = br.readLine();
			if (line == null) return null;
			String[] sa = line.split("\\s+");
			followee = Integer.parseInt(sa[0]);
			follower = Integer.parseInt(sa[1]);
		}
		
		this.last_followee = followee;
		if(followee > max_user_id) {
			return null;
		}
		
		return new TwitterGraphEdge(follower,followee);
	}

	public void close() throws IOException {
		br.close();
	}

}
