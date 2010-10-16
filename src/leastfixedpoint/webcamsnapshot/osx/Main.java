package leastfixedpoint.webcamsnapshot.osx;

// Incorporating techniques and some code from
// LiveCam.java by Jochen Broz on 19.02.05,
// http://lists.apple.com/archives/quicktime-java/2005/Feb/msg00062.html

// Based on camcapture for OSX - http://github.com/tonyg/camstream

import quicktime.QTSession;
import quicktime.QTException;
import quicktime.io.QTFile;
import quicktime.qd.QDRect;
import quicktime.qd.QDGraphics;
import quicktime.std.StdQTException;
import quicktime.std.sg.SequenceGrabber;
import quicktime.std.sg.SGVideoChannel;
import quicktime.std.sg.SGDeviceList;
import quicktime.std.sg.SGDeviceName;
import quicktime.util.RawEncodedImage;

import java.io.File;
import java.io.IOException;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import javax.imageio.ImageIO;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ImageIcon;

public class Main {
    public static void main (String args[]) {
	try {
	    QTSession.open();
            new Main(args);
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    QTSession.close();
	}
        System.exit(0);
    }

    public JFrame frame = new JFrame();
    public JLabel iconLabel = new JLabel();
    public String devicePattern;

    public void pickDevice(SGVideoChannel channel)
	throws Exception
    {
	SGDeviceList devList = channel.getDeviceList(0);
	for (int i = 0; i < devList.getCount(); i++) {
	    SGDeviceName name = devList.getDeviceName(i);
	    String nameName = name.getName();
	    System.out.println(nameName);
	    if (nameName.indexOf(devicePattern) != -1) {
		System.out.println("*** Selecting " + nameName);
		channel.setDevice(nameName);
	    }
	}
	//channel.settingsDialog();
    }

    public Main(String[] args)
	throws Exception
    {
	frame.setTitle("webcam-snapshot-osx");
	frame.add(iconLabel);

	int width = Integer.parseInt(args[0]);
	int height = Integer.parseInt(args[1]);
	String outputfile = args[2];
	String tempfile = args[3];
	devicePattern = args[4];

	java.util.Iterator iter = buildFrameIterator(width, height);

	while (iter.hasNext()) {
	    BufferedImage i = (BufferedImage) iter.next();
	    iconLabel.setIcon(new ImageIcon(i));
	    frame.pack();
	    frame.setVisible(true);
	    File o = new File(outputfile);
	    File t = new File(tempfile);
	    ImageIO.write(i, "png", t);
	    t.renameTo(o);
	}
    }

    public SequenceGrabber grabber;
    public RawEncodedImage rawImage;
    public int[] pixelData;
    public BufferedImage image;

    public java.util.Iterator buildFrameIterator(int desiredWidth,
                                                 int desiredHeight)
        throws Exception
    {
	grabber = new SequenceGrabber();
	SGVideoChannel vc = new SGVideoChannel(grabber);
	QDRect desiredBounds = new QDRect(0, 0, desiredWidth, desiredHeight);

	vc.setBounds(desiredBounds);
	vc.setUsage(quicktime.std.StdQTConstants.seqGrabRecord |
		    quicktime.std.StdQTConstants.seqGrabPreview |
		    quicktime.std.StdQTConstants.seqGrabPlayDuringRecord);
	vc.setFrameRate(0);
	vc.setCompressorType(quicktime.std.StdQTConstants.kComponentVideoCodecType);
	pickDevice(vc);

	QDGraphics gWorld = new QDGraphics(desiredBounds);
	grabber.setGWorld(gWorld, null);

	rawImage = gWorld.getPixMap().getPixelData();
	int size = rawImage.getSize();
	int intsPerRow = rawImage.getRowBytes()/4;

	size = intsPerRow * desiredBounds.getHeight();
	pixelData = new int[size];
	DataBuffer db = new DataBufferInt(pixelData, size);

	ColorModel colorModel = new DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff);
	int[] masks= {0x00ff0000, 0x0000ff00, 0x000000ff};
	WritableRaster raster = Raster.createPackedRaster(db,
							  desiredBounds.getWidth(),
							  desiredBounds.getHeight(),
							  intsPerRow,
							  masks,
							  null);
	image = new BufferedImage(colorModel, raster, false, null);

	QTFile movieFile = new QTFile(new java.io.File("NoFile"));
	grabber.setDataOutput( null, quicktime.std.StdQTConstants.seqGrabDontMakeMovie);
	grabber.prepare(true, true);
	grabber.startRecord();

        return new java.util.Iterator() {
                boolean notDead = true;
                public boolean hasNext() { return notDead; }
                public Object next() {
                    try {
                        grabber.idleMore();
                        grabber.update(null);
                        rawImage.copyToArray(0, pixelData, 0, pixelData.length);
                    } catch (QTException qte) {
                        notDead = false;
                    }
                    return image;
                }
                public void remove() {}
            };
    }
}
