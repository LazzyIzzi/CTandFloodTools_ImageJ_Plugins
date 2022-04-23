package CT_Tools;
/*
 * An example plugin that calls several of the functions available in MuMassCalc_J8Lib.jar
 * MuMassCalc_J8Lib.jar is derived from the NIST XCOM web application used to calculate 
 * photon cross sections for scattering, photoelectric absorption and pair production,
 * as well as total attenuation coefficients, for any element, compound (Z less than o equal to 100),
 * at energies from 1 keV to 100 GeV.
 * See: https://github.com/LazzyIzzi/MuMassCalculator
 * 
 * This plugin supports two operations:
 * 1. Interactive calculation of interpolated attenuation cross-sections in units of cm2/gm from a
 * user-supplied simplified chemical formula and the photon energy. Calculated values 
 * are posted to a results window.
 * 
 * 2.Plotting of tabulated absorption spectra for selected cross-sections for the user supplied
 * formula and energy range.  Optionally the absorption edge energies for the atoms in the formula
 * are reported in a separate results table.
 * 
 */

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import ij.plugin.*;
import ij.gui.*;
import ij.*;
import ij.measure.*;

import jhd.MuMassCalculator.*;
import tagTools.common.*;
import gray.AtomData.*;


public class Xray_Calculator implements PlugIn, ActionListener, DialogListener {

	MuMassCalculator mmc= new MuMassCalculator();
	MatlListTools2 mlt=new MatlListTools2();
	MatlListTools2.TagSet tagSet;
	String[] matlName;
	String[] matlFormula;
	double[] matlGmPerCC;
	
	String myDialogTitle = "Mu Mass Calculator";
	GenericDialog gd;
	int dlogW,dlogH,dlogL,dlogT;
	
	PlotWindow gPlotWindow;
	final int plotW=625,plotH=350,resultW=650,resultH=190,calcW=650,calcH=220;
	final Color buff = new Color(250,240,200);
	
	//Local use variables, not used by MuMassCalculator
	double gmPerCC;
	String formulaName;
	
	Font myFont = new Font(Font.DIALOG, Font.ITALIC+Font.BOLD, 14);	

	//***********************************************************************************/

	protected class MMCsettings
	{
		String formula;;
		double meV;
		double minMeV;
		double maxMeV;
		boolean plotMeVLogScale;
		boolean[] muMassSelections;
		boolean plotMuMassLogScale;
		boolean reportEdges;
	}	
	MMCsettings mmcSet = new MMCsettings();
	
	//***********************************************************************************/
	
	@Override
	public void run(String arg)
	{
		if(IJ.versionLessThan("1.53k")) return;
		
		//Location of the default materials list
		String dir = IJ.getDirectory("plugins");
		String defaultFilePath = dir + "DialogData\\DefaultMaterials.csv";
		
		tagSet = mlt.loadTagFile(defaultFilePath);
		//Get names array from TagSet
		matlName = new String[tagSet.tagData.size()];
		matlFormula = new String[tagSet.tagData.size()];
		matlGmPerCC = new double[tagSet.tagData.size()];
		int i=0;
		for(MatlListTools2.TagData td : tagSet.tagData)
		{
			matlName[i]= td.matlName;
			matlFormula[i] = td.matlFormula;
			matlGmPerCC[i] = td.matlGmPerCC;
			i++;
		}
		DoDialog();
		
//		String dir = IJ.getDirectory("plugins");
//		dir= dir.replace("\\","/");
//		String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";
	}

	
	//*****************************************************************************
	
	private void DoDialog()
	{
		gd =  GUI.newNonBlockingDialog(myDialogTitle);
		
		double theKeV = 100.0;
		
		gd.setInsets(0, 0, 0);
		gd.addMessage("Compute absorption\n"
				+ "cross-sections in cm2/gm",myFont);
		gd.setInsets(5, 0, 0);
		gd.addMessage("Formula Format\n"
				+ "Atom1:Count1:Atom2:Count2 etc.", myFont);
		gd.addChoice("Material Name", matlName, matlName[0]); //myTags.matlName[0] is column header title
		gd.addStringField("Formula: ",  matlFormula[0],18);
		gd.addNumericField("Density", matlGmPerCC[0]);
		gd.addNumericField("KeV:", theKeV);
		gd.setInsets(10, 120, 0);
		gd.addButton("Calculate", this);
		gd.addMessage("_____________________________",myFont);
		gd.addMessage("Plot tabulated absorption\n"
				+ "cross-sections in cm2/gm",myFont);
		gd.addMessage("Energy Range", myFont);		
		gd.addNumericField("Min KeV", 1,12);
		gd.addNumericField("Max KeV", 100000000,12);
		gd.addCheckbox("Plot MeV Log scale", true);
		gd.addCheckbox("Plot cm2/gm on Log scale", true);
		gd.addCheckbox("List absorption edge energies", false);
		gd.addMessage("Cross-section",myFont);
		String[] muMassTypes = mmc.getMuMassTypes();
		gd.addCheckbox(muMassTypes[0], true);
		for(int i=1;i<muMassTypes.length;i++)
		{
			gd.addCheckbox(muMassTypes[i], false);
		}
		gd.setInsets(0, 120, 0);
		gd.addButton("Update Plot", this);
		gd.addMessage("_____________________________",myFont);
		gd.addHelp("https://lazzyizzi.github.io/XrayCalculator.html");
		gd.addDialogListener(this);
		
		
		gd.showDialog();
		
		if (gd.wasCanceled())
		{
			if(gPlotWindow!=null) gPlotWindow.close();
			ResultsTable rt;
			rt = ResultsTable.getResultsTable("Absorption Edges(KeV)");
			if(rt!=null)
			{			
				IJ.selectWindow("Absorption Edges(KeV)");
				IJ.run("Close");
			}
			rt = ResultsTable.getResultsTable("QuickCalc");
			if(rt!=null)
			{			
				IJ.selectWindow("QuickCalc");
				IJ.run("Close");
			}
			return;
		}	
	}
	
	//*********************************************************************************/
	
	private boolean ValidateParams()
	{
		boolean paramsOK = true;
		double[] mevList = mmc.getMevArray(mmcSet.formula);
		if(mevList==null)
		{
			IJ.showMessage("Error", mmcSet.formula + " Bad Formula, Element or count missing");
			paramsOK = false;
		}
		if(mmcSet.meV < 0.001 || mmcSet.meV > 100000)
		{
			IJ.showMessage("Error:  KeV Out of range  1 < KeV < 100,000,000");
			paramsOK = false;
		}		
		return paramsOK;		
	}

	//*********************************************************************************/

	@SuppressWarnings("unchecked")
	private void GetSelections(GenericDialog gd)
	{
		
		Vector<Choice> choices= gd.getChoices();
		int index = choices.get(0).getSelectedIndex();
		
		Vector<TextField> textFields = gd.getStringFields();
		textFields.get(0).setText(matlFormula[index]);
		mmcSet.formula = textFields.get(0).getText();
		formulaName = matlName[index];
		
		String str;
		Vector<TextField> numberFields = gd.getNumericFields();
		str = numberFields.get(0).getText();
		if(isNumeric(str)) gmPerCC = Double.parseDouble(str);
		
		str = numberFields.get(1).getText();
		if(isNumeric(str)) mmcSet.meV = Double.parseDouble(str)/1000;
		
		str = numberFields.get(2).getText();
		if(isNumeric(str)) mmcSet.minMeV = Double.parseDouble(str)/1000;
		
		str = numberFields.get(3).getText();
		if(isNumeric(str)) mmcSet.maxMeV = Double.parseDouble(str)/1000;
				
		Vector<Checkbox> checkBoxes = gd.getCheckboxes();
		mmcSet.plotMeVLogScale = checkBoxes.get(0).getState();
		mmcSet.plotMuMassLogScale = checkBoxes.get(1).getState();
		mmcSet.reportEdges = checkBoxes.get(2).getState();
		
		mmcSet.muMassSelections = new boolean[mmc.getMuMassTypes().length];
		
		for(int i = 0;i< mmc.getMuMassTypes().length;i++)
		{
			mmcSet.muMassSelections[i] = checkBoxes.get(3+i).getState();
		}		
	}
	public static boolean isNumeric(String str)
	{ 
		try
		{  
			Double.parseDouble(str);  
			return true;
		}
		catch(NumberFormatException e)
		{  
			return false;  
		}  
	}
		
	//*********************************************************************************/
	
	private void UpdateResults()
	{
		GetSelections(gd);

		dlogW = gd.getSize().width;
		dlogH = gd.getSize().height;
		dlogL = gd.getLocation().x;
		dlogT = gd.getLocation().y;
		ResultsTable rt = ResultsTable.getResultsTable("QuickCalc");
		if(rt==null)
		{
			rt=new ResultsTable();
			rt.setPrecision(5);
		}
		
		try
		{
			String[] mmTypes =mmc.getMuMassTypes();
			String myFormula = mmcSet.formula;
			ArrayList<AtomData> fl = mmc.createFormulaList(myFormula);
			
			double myKeV = mmcSet.meV*1000; //.parseDouble(meV.getText());
			if(fl != null && myKeV> 0.001 && myKeV < 100000)
			{
				//rt.addRow();
				rt.incrementCounter();
				rt.addValue("Name", formulaName);
				rt.addValue("Formula", myFormula);
				rt.addValue("gm/cc", gmPerCC);
				rt.addValue("KeV", myKeV);
				try
				{
					rt.addValue("Linear Attn cm"+(char)0x207b+(char)0x0b9, gmPerCC * mmc.getMuMass(myFormula, myKeV/1000, mmTypes[0]));
					for(int i=0;i<mmTypes.length;i++)
					{
						double muMass = mmc.getMuMass(myFormula, myKeV/1000, mmTypes[i]); 				
						rt.addValue(mmTypes[i], muMass);
					}
					rt.show("QuickCalc");
					Window win = WindowManager.getWindow("QuickCalc");
					//win.setLocation(dlogL+dlogW,dlogT+plotH+resultH);
					win.setLocation(dlogL+dlogW,dlogT);
					win.setSize(calcW,calcH);
				}
				catch(Exception e1)
				{
					//do Nothing
				} 
			}
		}
		catch(Exception e1)
		{
		}
	}
	
	//*********************************************************************************/

	private void UpdatePlot()
	{
		int i,j;
		
		//Plot energies must be between 1 and 100,000,000 KeV
		if(mmcSet.minMeV < 0.001 || mmcSet.maxMeV > 100000)
		{
			IJ.showMessage("Plot KeV Out of range.   1 < KeV < 100,000,000");
			return;
		}
		
		dlogW = gd.getSize().width;
		dlogH = gd.getSize().height;
		dlogL = gd.getLocation().x;
		dlogT = gd.getLocation().y;

		// Copy the settings to local variables to make the code
		//more readable and easier to debug
		String formula = mmcSet.formula;
		double minMeV = mmcSet.minMeV;
		double maxMeV = mmcSet.maxMeV;
		boolean[] muMassSelections = mmcSet.muMassSelections;
		String[] mmTypes = mmc.getMuMassTypes();
		boolean xLogPlot = mmcSet.plotMeVLogScale;
		boolean yLogPlot = mmcSet.plotMuMassLogScale;
		boolean reportEdges = mmcSet.reportEdges;
		

		//give each muMassType a distinct color
		Color[] myColors = new Color[6];
		myColors[0]= Color.black;
		myColors[1]= Color.blue;
		myColors[2]= Color.red;
		myColors[3]= Color.green;
		myColors[4]= Color.cyan;
		myColors[5]= Color.magenta;
		
		//Get the tabulated energies associated with atoms in the formula
		double[] meVArr = mmc.getMevArray(formula,minMeV,maxMeV);
		
		if(meVArr==null || meVArr.length == 0)
		{
			IJ.showMessage("There is no tabulated data for " + formula + " between " + mmcSet.minMeV*1000 + " and " +mmcSet.maxMeV*1000+ " KeV"
					+ "\nPlease select a wider range to plot.");
			return;
		}
		

		//count the number of muMassTypes selected++++++++++++++++++++++++++
		int cnt=0;
		for(i=0;i<muMassSelections.length;i++)
		{
			if(muMassSelections[i]) cnt++;
		}

		//create a 2D array to hold each spectrum
		double[][] muMass = new double[cnt][meVArr.length];

		//Get the mass attenuation coefficients of the formula++++++++++++++
		//for each checked flag and for each meV
		cnt=0;
		String legend="";
		for(i=0;i<muMassSelections.length;i++)
		{
			if(muMassSelections[i])
			{
				for(j=0;j<meVArr.length;j++)
				{
					muMass[cnt][j] = mmc.getMuMass(formula,meVArr[j],mmTypes[i]);
				}
				legend +=mmTypes[i] + "\t";
				cnt++;
			}
		}

		//Find min and max MuMass+++++++++++++++++++++++++++++++++++++++++++++
		double muMassMax = Double.MIN_VALUE;
		double muMassMin = Double.MAX_VALUE;
		for(j=0;j<cnt;j++)
		{
			for(i=0;i<meVArr.length;i++)
			{
				if(muMassMax < muMass[j][i]) muMassMax = muMass[j][i];
				if(muMassMin > muMass[j][i] && muMass[j][i]!=0) muMassMin = muMass[j][i];
			}
		}		


//Plot the  spectra+++++++++++++++++++++++++++++++++++++++++++++++++++
		
		String plotTitle = "Mass Attenuation";

		//If the user closes the plot window but the gPlotWindow is not set to null
		if(gPlotWindow!=null && gPlotWindow.isClosed())
		{
			this.gPlotWindow.dispose();//probably already called by the close() method
			this.gPlotWindow=null;
			//System.gc(); //This may be overkill
		}

		if(gPlotWindow==null)
		{
			//Convert MeV to KeV
			double[] keVArr = new double[meVArr.length];
			for(i = 0; i< meVArr.length;i++)
			{
				keVArr[i] = meVArr[i]*1000;
			}
			Plot plot = new Plot(plotTitle,"KeV","cm2/gm");
			plot.setFont(Font.BOLD, 16);
			plot.setLimits(keVArr[0], keVArr[meVArr.length-1], muMassMin, muMassMax);
			plot.setSize(plotW, plotH);
			plot.setBackgroundColor(buff);
			

			//add the spectra to the plot
			//the circles are used to show the positions of the tabulated NIST data
			//from mmc.getMevArray(formula,minMev,maxMev);
			cnt=0;
			for(i=0;i<muMassSelections.length;i++)
			{
				if(muMassSelections[i])
				{
					plot.setColor(myColors[i]);
					plot.addPoints(keVArr,muMass[cnt],Plot.CONNECTED_CIRCLES);
					cnt++;
				}
			}

			plot.setLineWidth(1);
			if(xLogPlot)
			{
				plot.setLogScaleX();
			}
			if(yLogPlot)
			{
				plot.setLogScaleY();
			}
			plot.addLegend(legend);
			plot.setColor(Color.BLACK);
			plot.addLabel(0, 0.9, formulaName+"\n" +formula);
			gPlotWindow = plot.show();
			WindowManager.getWindow(plotTitle).setLocation(dlogL+dlogW,dlogT+calcH);
		}
		else
		{
			//double[] oldPlotLimits = gPlotWindow.getPlot().getLimits();
			//Convert MeV to KeV
			double[] keVArr = new double[meVArr.length];
			for(i = 0; i< meVArr.length;i++)
			{
				keVArr[i] = meVArr[i]*1000;
			}
	
			Plot newPlot = new Plot(plotTitle,"KeV","cm2/gm");
			newPlot.setFont(Font.BOLD, 16);
			//add the spectra to the plot
			//the circles are used to show the positions of the tabulated NIST data
			cnt=0;
			for(i=0;i<muMassSelections.length;i++)
			{
				if(muMassSelections[i])
				{
					newPlot.setColor(myColors[i]);
					newPlot.addPoints(keVArr,muMass[cnt],Plot.CONNECTED_CIRCLES);
					cnt++;
				}
			}

			newPlot.setLineWidth(1);
			if(xLogPlot)
			{
				newPlot.setLogScaleX();
			}
			if(yLogPlot)
			{
				newPlot.setLogScaleY();
			}
			newPlot.addLegend(legend);
			//newPlot.setLimits(meVArr[0], meVArr[meVArr.length-1], oldPlotLimits[2], oldPlotLimits[3]);
			newPlot.setLimits(keVArr[0], keVArr[keVArr.length-1], muMassMin, muMassMax);
			newPlot.setColor(Color.BLACK);
			newPlot.addLabel(0, 0.9, formulaName+"\n" +formula);
			newPlot.setSize(plotW, plotH);			
			newPlot.setBackgroundColor(buff);

			gPlotWindow.drawPlot(newPlot);
			WindowManager.getWindow(plotTitle).setLocation(dlogL+dlogW,dlogT+calcH);
		}
		
		//Report edges+++++++++++++++++++++++++++++++++++++++++++++++++++++
		//The imageJ plot.addLabel methods are not supported by the plot window
		//re-scale and zoom features, i.e. the labels don't move when zoomed
		//so the edge label feature has been dropped.
		if(reportEdges)
		{
			ArrayList<AtomData> fl = mmc.createFormulaList(formula);
			String[] edgeNames = mmc.getabsEdgeNames();		
			ResultsTable rt = ResultsTable.getResultsTable("Absorption Edges(KeV)");
			if(rt==null)
			{
				rt = new ResultsTable();
				rt.setPrecision(4);
			}
			boolean atomFound = false;
			for(i=0;i< fl.size();i++)
			{
				String theAtom = mmc.getAtomSymbol(fl, i);
				String atm = theAtom;
				//Add the atom if not already listed
				for(j=0;j< rt.size();j++)
				{
					if(atm.toUpperCase().equals(rt.getStringValue("Name", j).toUpperCase()))
					{
						atomFound=true;
						break;
					}
				}
				if(atomFound==false)
				{
					rt.incrementCounter();
					rt.addValue("Name",theAtom);
					for(String edge : edgeNames)
					{	
						double edgeMev = mmc.getAtomAbsEdge(theAtom ,edge ) ;
						if(edgeMev > 0)
						{
							//rt.addValue(edge, ResultsTable.d2s(edgeMev, 5));
							rt.addValue(edge, edgeMev*1000);
						}
					}
				}
			}
			rt.show("Absorption Edges(KeV)");
			Point plotLoc = WindowManager.getWindow(plotTitle).getLocation();
			int plotHeight = WindowManager.getWindow(plotTitle).getHeight();
			//What!! no "With" construct!!
			WindowManager.getWindow("Absorption Edges(KeV)").setLocation(plotLoc.x,plotLoc.y+ plotHeight);
			WindowManager.getWindow("Absorption Edges(KeV)").setSize(resultW,resultH);
		}

	}
	
	//*********************************************************************************/

	public void actionPerformed(ActionEvent theEvent)
	{
		GetSelections(gd);
		String cmd = theEvent.getActionCommand();
		switch(cmd)
		{
		case "Calculate":
			if (ValidateParams())
				UpdateResults();
			break;
		case "Update Plot":
			if (ValidateParams())
				UpdatePlot();
			break;
		}
	}

	//*********************************************************************************/

	@SuppressWarnings("unchecked")
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		//Component[] dComp = gd.getComponents();
		if(e!=null)
		{
			if(e.getSource() instanceof Choice)
			{
				Vector<Choice> choices= gd.getChoices();
				int index = choices.get(0).getSelectedIndex();
				Vector<TextField> textFields = gd.getStringFields();
				Vector<TextField> numberFields = gd.getNumericFields();
				textFields.get(0).setText(matlFormula[index]);
				numberFields.get(0).setText(Double.toString(matlGmPerCC[index]));
				formulaName = matlName[index];
			}
			return true;
		}
		else return false;
	}
}
