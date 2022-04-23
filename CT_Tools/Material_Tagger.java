package CT_Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import tagTools.common.*;

import java.awt.event.*;
import java.awt.*;


/**A tool for creating "tagged" images. Uses a Materials LIst JFrame viewer to show tags.
 * The viewer cannot be accessed by the user because of the parent genericDialog
 * The integer tag values are the indices of a  list of material names, formulas and densities.
 * A tag's formula and density is used to compute linear attenuation at a single photon energy.	
 * JHD 12/4/2021
*/

public class Material_Tagger implements PlugInFilter, DialogListener, ActionListener
{
	MatlListTools2 mlt = new MatlListTools2();
	MatlListTools2.TagSet myTagSet;
	String[] matlNames;
	int		matlIndex;	// the position of the material in the list
	String	path;		// a file path for saving the dialog box values
	float	low,high;
	
	GenericDialog gd;
	ImagePlus imp;
	ImagePlus tagImp;
	ImageProcessor ip;
	
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);	
	
	//*****************************************************************
	
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		// TODO Auto-generated method stub
		this.imp=imp;
		return DOES_32;
	}

	//*****************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		this.ip = ip;
		if(IJ.versionLessThan("1.41k")) return;
		Calibration cal = imp.getCalibration();
		if(!cal.getUnit().equals("cm"))
		{
			IJ.error("Pixel units must be cm");
			return;
		}
		String dir = IJ.getDirectory("plugins");
		String path = dir + "DialogData\\DefaultMaterials.csv";
		myTagSet = mlt.loadTagFile(path);
		//Get names array from TagSet
		matlNames = new String[myTagSet.tagData.size()];
		int i=0;
		for(MatlListTools2.TagData td : myTagSet.tagData)
		{
			matlNames[i]= td.matlName;
			i++;
		}
		
		if(myTagSet!=null)
		{
			tagImp = IJ.createImage("TagImage", imp.getWidth(), imp.getHeight(), 1, imp.getBitDepth());
			tagImp.setCalibration(cal);
			tagImp.show();
			int left = imp.getWindow().getX();
			int top = imp.getWindow().getY();
			tagImp.getWindow().setLocation(left+imp.getWidth() + 20, top);
			
			gd = new GenericDialog("Material Tagger");
			//Materials
			//gd.setInsets(10,0,0);
			gd.addMessage("Select a material from the menu.\nMove the sliders to select thresholds.\nClick \"Add \" button.\nClick \"OK\" when done.",myFont,Color.BLACK);
			//gd.setInsets(10,50,0);
			gd.addChoice("Material: ",matlNames,matlNames[1]);
			ImageStatistics stats = imp.getStatistics();
			gd.addSlider("Lower", stats.min, stats.max, stats.min);
			gd.addSlider("Upper", stats.min, stats.max, stats.max);
			//gd.setInsets(10,50,0);
			gd.addButton("Add Material to Tag Image", this);
			gd.addDialogListener(this);
			gd.addHelp("https://lazzyizzi.github.io/MaterialTagger.html");
			gd.showDialog();
			
			if(gd.wasOKed())
			{
				ip.resetThreshold();
				imp.updateAndDraw();				
			}
			if(gd.wasCanceled())
			{
				tagImp.close();
				return;
			}
		}
		else return;		
	}
	
	//*****************************************************************
	
	private void GetSettings()
	{
		matlIndex = gd.getNextChoiceIndex();
		low = (float)gd.getNextNumber();
		high = (float)gd.getNextNumber();	
	}

	//*****************************************************************
	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
	   	GetSettings();
		ip.setThreshold(low, high, ImageProcessor.RED_LUT);
		imp.updateAndDraw();
		return true;
	}

	//*****************************************************************

	@Override
	public void actionPerformed(ActionEvent e)
	{
		Button btn = (Button)e.getSource();
		String btnLabel = btn.getLabel();
		switch(btnLabel)
		{
		case "Add Material to Tag Image":
			GetSettings();
			if(matlIndex>0)
			{
				float[] pix = (float[])ip.getPixels();
				float[] tagPix = (float[])tagImp.getProcessor().getPixels();
				for(int i=0;i<pix.length;i++)
				{
					if(pix[i]<=high && pix[i] >= low)
					{
						tagPix[i] = myTagSet.tagData.get(matlIndex).matlTag;
					}
				}
				ip.resetThreshold();
				imp.updateAndDraw();
				tagImp.getProcessor().setMinAndMax(tagImp.getStatistics().min, tagImp.getStatistics().max);
				IJ.run(tagImp, "3-3-2 RGB", "");
				tagImp.updateAndDraw();
			}
			break;
		}
	}
    
}
