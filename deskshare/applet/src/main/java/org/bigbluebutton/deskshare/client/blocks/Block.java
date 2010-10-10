/*
 * BigBlueButton - http://www.bigbluebutton.org
 * 
 * Copyright (c) 2008-2009 by respective authors (see below). All rights reserved.
 * 
 * BigBlueButton is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 3 of the License, or (at your option) any later 
 * version. 
 * 
 * BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with BigBlueButton; if not, If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id: $
 */
package org.bigbluebutton.deskshare.client.blocks;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Adler32;

import org.bigbluebutton.deskshare.client.net.EncodedBlockData;
import org.bigbluebutton.deskshare.common.PixelExtractException;
import org.bigbluebutton.deskshare.common.ScreenVideoEncoder;
import org.bigbluebutton.deskshare.common.Dimension;

public final class Block {   
	Random random = new Random();
    private final Adler32 checksum;
    private final Dimension dim;
    private final int position;
    private final Point location;    
    private int[] pixels;
    private AtomicBoolean dirtyFlag = new AtomicBoolean(false);
    private long lastSent = System.currentTimeMillis();
    
    Block(Dimension dim, int position, Point location) {
        checksum = new Adler32();
        this.dim = dim;
        this.position = position;
        this.location = location;
    }
    
    public boolean hasChanged(BufferedImage capturedScreen) {	 
    	synchronized(this) {
            try {
        		pixels = ScreenVideoEncoder.getPixels(capturedScreen, getX(), getY(), getWidth(), getHeight());
            } catch (PixelExtractException e) {
            	System.out.println(e.toString());
        	}  
            
            if ((! checksumSame(pixels)) || sendKeepAliveBlock()) {
            	if (dirtyFlag.compareAndSet(false, true)) {
            		return true;
            	} 
            } 
    	}
    	 		    	
        return false;
    }
         
    private boolean isKeepAliveBlock() {
    	// Use block 1 as our keepalive block. The keepalive block is our audit so that the server knows
    	// that the applet is still connected to the server. So it there's no change in the desktop, the applet
    	// should still send this keepalive block.
    	return position == 1;
    }
    
    private boolean sendKeepAliveBlock() {
    	long now = System.currentTimeMillis();
    	if (isKeepAliveBlock() && (now - lastSent > 30000)) {
    		// Send keepalive block every 30 seconds.
    		lastSent = now;
    		System.out.println("Sending keep alive block!");
    		return true;
    	}
    	return false;
    }
    
    public EncodedBlockData encode() {   
    	int[] pixelsCopy = new int[pixels.length];
    	
    	synchronized (this) { 
    		/*
    		 * Make sure we update here so that the screen capture thread will
    		 * be able to mark the block if it has changed while we send the
    		 * last captured block.
    		 */
    		dirtyFlag.compareAndSet(true, false);
    		checksum.update(0);
            System.arraycopy(pixels, 0, pixelsCopy, 0, pixels.length);
		}
    	
        byte[] encodedBlock = ScreenVideoEncoder.encodePixels(pixelsCopy, getWidth(), getHeight()); 	
        return new EncodedBlockData(position, encodedBlock);		
    }
    
    private boolean checksumSame(int[] pixels) {
    	long oldsum = checksum.getValue(); 
    	checksum.reset();
    	checksum.update(convertIntPixelsToBytePixels(pixels)); 
        return (oldsum == checksum.getValue());
    }
          
    private byte[] convertIntPixelsToBytePixels(int[] pixels) {
    	byte[] p = new byte[pixels.length * 3];
    	int position = 0;
		
		for (int i = 0; i < pixels.length; i++) {
			byte red = (byte) ((pixels[i] >> 16) & 0xff);
			byte green = (byte) ((pixels[i] >> 8) & 0xff);
			byte blue = (byte) (pixels[i] & 0xff);

			// Sequence should be BGR
			p[position++] = blue;
			p[position++] = green;
			p[position++] = red;
		}
		
		return p;
    }
    
    public int getWidth() {
        return new Integer(dim.getWidth()).intValue();
    }
    
    public int getHeight() {
        return new Integer(dim.getHeight()).intValue();
    }
    
    public int getPosition() {
		return new Integer(position).intValue();
	}
    
    public int getX() {
		return new Integer(location.x).intValue();
	}

    public int getY() {
		return new Integer(location.y).intValue();
	}
	
    Dimension getDimension() {
		return new Dimension(dim.getWidth(), dim.getHeight());
	}
	
    Point getLocation() {
		return new Point(location.x, location.y);
	}
}
