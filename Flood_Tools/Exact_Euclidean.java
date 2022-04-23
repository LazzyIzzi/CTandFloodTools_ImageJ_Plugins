package Flood_Tools;

import java.awt.Color;
import java.awt.Font;

import ij.IJ;
import ij.gui.*;
import ij.measure.Calibration;
import ij.ImagePlus;
import ij.ImageStack;
//import ij.plugin.ContrastEnhancer;
import ij.plugin.filter.*;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

//import jhd.DistanceMaps.libJ8.*;
import jhd.FloodFill.ExactEuclideanMap;

public class Exact_Euclidean implements PlugInFilter
{


	ImagePlus imp;
	ExactEuclideanMap myEDM = new ExactEuclideanMap();
	String[] destChoices3D = {"3D new Image","3D in Place","2D All Slices New Image","2D All Slices in Place", "2D Current Slice New Image"};
	String[] destChoices2D = {"2D All Slices New Image","2D All Slices in Place"};
	String[] destChoices = null;

	//*********************************************************************************************

	class DialogParams
	{
		public String floodChoice;
		public String destChoice;
		public boolean useSize;
	}

	//*********************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_ALL;
	}

	//*********************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		ImageStack inStack = imp.getStack();
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = inStack.getSize();
		int i;
		
		float pixWidth=1,pixHeight=1,pixDepth=1;
	
		DialogParams dp = DoMyDialog();
		
		if(dp!=null)
		{
			if(ValidateParams(imp,dp))
			{
				if(dp.useSize)
				{
					Calibration impCal = imp.getCalibration();
					pixWidth = (float)impCal.pixelWidth;
					pixHeight = (float)impCal.pixelHeight;
					pixDepth = (float)impCal.pixelDepth;
				}

				String title;
				title = imp.getTitle();
				ImagePlus myImp=null;
				ImageConverter imgConvert;
				switch(dp.destChoice)
				{
				case "3D new Image":					
					myImp = imp.duplicate();
					imgConvert = new ImageConverter(myImp);				
					ImageConverter.setDoScaling(false);			
					imgConvert.convertToGray32();
					myEDM.edm3D(myImp.getStack().getImageArray(), width, height, depth, pixWidth, pixHeight, pixDepth, dp.floodChoice, true);
					myImp.setTitle(title + "_3D");
					myImp.show();
					break;
					
				case "3D in Place":
					imgConvert = new ImageConverter(imp);				
					ImageConverter.setDoScaling(false);			
					imgConvert.convertToGray32();					
					myEDM.edm3D(imp.getStack().getImageArray(), width, height, depth, pixWidth, pixHeight, pixDepth, dp.floodChoice, true);
					imp.setTitle(title + "_3D");
					imp.show();
					break;
					
				case"2D All Slices New Image":
					myImp = imp.duplicate();
					imgConvert = new ImageConverter(myImp);				
					ImageConverter.setDoScaling(false);			
					imgConvert.convertToGray32();

					IJ.showStatus("Run 2D EDM " + dp.destChoice + " " + dp.floodChoice);		
					for(i=1;i<=myImp.getNSlices();i++)
					{
						myEDM.edm2D(myImp.getStack().getPixels(i), width, height, pixWidth, pixHeight, dp.floodChoice);
					}					
					myImp.setTitle(title + "_2D");
					myImp.show();
					break;

				case"2D All Slices in Place":
					imgConvert = new ImageConverter(imp);				
					ImageConverter.setDoScaling(false);			
					imgConvert.convertToGray32();

					IJ.showStatus("Run 2D EDM " + dp.destChoice + " " + dp.floodChoice);		
					for(i=1;i<=imp.getNSlices();i++)
					{
						myEDM.edm2D(imp.getStack().getPixels(i), width, height, pixWidth, pixHeight, dp.floodChoice);
					}
					imp.setTitle(title + "_2D");
					imp.show();
					break;
					
				case "2D Current Slice New Image":
					myImp = imp.crop("whole-slice");
					imgConvert = new ImageConverter(myImp);				
					ImageConverter.setDoScaling(false);			
					imgConvert.convertToGray32();

					myEDM.edm2D(myImp.getProcessor().getPixels(), width, height, pixWidth, pixHeight, dp.floodChoice);

					myImp.setTitle(title +  "_2D");
					myImp.show();
					break;
				}
				IJ.run("Fire");
				IJ.run("Enhance Contrast", "saturated=0.35");
				//ce.stretchHistogram(myImp, 0.3);
			}
		}
	}


	//*********************************************************************************************

	private DialogParams DoMyDialog() {

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		if(imp.getStackSize() == 1)
		{
			destChoices = destChoices2D;
		}
		else
		{
			destChoices = destChoices3D;			
		}

		String[] floodChoices = {"Map 0","Map !0"};
		DialogParams dp = new DialogParams();
		GenericDialog gd = new GenericDialog("Euclidean Distance Map (EDM)");
		gd.addMessage("Convert binary image to Euclidean Distance.",myFont,Color.BLACK);
		gd.addRadioButtonGroup("Value to process", floodChoices, 1, 2, floodChoices[0]);
		gd.addChoice("Output",destChoices,destChoices[0]);
		gd.addCheckbox("Use Pixel Sizes", false);
		gd.addMessage("Notes:\nIn-place calculation converts the binary image to 32-Bit.",myFont,Color.BLACK);
		gd.addHelp("https://lazzyizzi.github.io/ExactEuclidean.html");
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		dp.floodChoice = gd.getNextRadioButton();
		dp.destChoice = gd.getNextChoice();
		dp.useSize = gd.getNextBoolean();

		return dp;
	}

	//*********************************************************************************************

	private boolean ValidateParams(ImagePlus theImp, DialogParams dp)
	{
		return true;
	}

	//*********************************************************************************************

}
