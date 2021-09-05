//package MuMassCalculator;
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
import gray.AtomData.*;


public class Xray_Calculator implements PlugIn, ActionListener, DialogListener {

	int   testCnt=0;
	String myDialogTitle = "Mu Mass Calculator";
	MuMassCalculator mmc= new MuMassCalculator();

	PlotWindow gPlotWindow;

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
		
		int i;

		String dir = IJ.getDirectory("plugins");
		dir= dir.replace("\\","/");
		String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";
		Font myFont = new Font(Font.DIALOG, Font.ITALIC+Font.BOLD, 14);	

		GenericDialog gd =  GUI.newNonBlockingDialog(myDialogTitle);
		
		String theFormula = "Pb:1:Cr:1:O:4";
		double theMeV = .1;
		
		gd.addMessage("Compute absorption cross-sections in cm2/gm",myFont);
		gd.addMessage("Formula Format = Atom1:Count1:Atom2:Count2 etc.", myFont);
		gd.addStringField("Formula: ", theFormula,26);
		gd.addNumericField("MeV:", theMeV);
		gd.setInsets(0, 250, 0);
		gd.addButton("Calculate", this);
		
		//gd.setInsets(10, 0, 0);
		gd.addMessage("___________________________________________",myFont);
		gd.addMessage("Plot tabulated absorption cross-sections in cm2/gm",myFont);
		gd.addMessage("Energy Range", myFont);		
		gd.addNumericField("Min MeV", .001);
		gd.addNumericField("Max MeV", 100000);
		gd.addCheckbox("Plot MeV Log scale", true);
		gd.addMessage("Cross-section",myFont);
		String[] muMassTypes = mmc.getMuMassTypes();
		gd.addCheckbox(muMassTypes[0], true);
		for(i=1;i<muMassTypes.length;i++)
		{
			gd.addCheckbox(muMassTypes[i], false);
		}
		gd.setInsets(25, 20, 0);
		gd.addCheckbox("Plot cross-section cm2/gm on Log scale", true);
		gd.addCheckbox("Report formula absorption edge energies", false);
		gd.setInsets(0, 250, 0);
		gd.addButton("Update Plot", this);
		gd.addMessage("___________________________________________",myFont);
		gd.addHelp(myURL);

		gd.addDialogListener(this);
		
		GetSelections(gd);

		gd.showDialog();

		if (gd.wasCanceled())
		{
			if(gPlotWindow!=null) gPlotWindow.close();
			ResultsTable rt;
			rt = ResultsTable.getResultsTable("Absorption Edges(MeV)");
			if(rt!=null)
			{			
				IJ.selectWindow("Absorption Edges(MeV)");
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
		//checks
		double[] mevList = mmc.getMevArray(mmcSet.formula);
		if(mevList==null)
		{
			IJ.showMessage("Error", mmcSet.formula + " Bad Formula, Element or count missing");
			return false;
		}
		else
		{
			return true;
		}
		
	}

	//*********************************************************************************/

	private void GetSelections(GenericDialog gd)
	{
		int i;
		mmcSet.formula = gd.getNextString();
		mmcSet.meV = gd.getNextNumber();

		mmcSet.minMeV = gd.getNextNumber();
		mmcSet.maxMeV = gd.getNextNumber();
		mmcSet.plotMeVLogScale = gd.getNextBoolean();
		
		mmcSet.muMassSelections = new boolean[mmc.getMuMassTypes().length];
		
		for(i = 0;i< mmc.getMuMassTypes().length;i++)
		{
			mmcSet.muMassSelections[i] = gd.getNextBoolean();
		}
		mmcSet.plotMuMassLogScale = gd.getNextBoolean();
		mmcSet.reportEdges = gd.getNextBoolean();
		
	}
	
	//*********************************************************************************/
	
	private void UpdateResults()
	{
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
			
			double myMeV = mmcSet.meV; //.parseDouble(meV.getText());
			if(fl != null && myMeV> 0.001 && myMeV < 100000)
			{
				//rt.addRow();
				rt.incrementCounter();
				rt.addValue("Formula", myFormula);
				rt.addValue("MeV", myMeV);
				try
				{
					for(int i=0;i<mmTypes.length;i++)
					{
						double muMass = mmc.getMuMass(myFormula, myMeV, mmTypes[i]); 				
						rt.addValue(mmTypes[i], muMass);
					}
					rt.show("QuickCalc");
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
		if(meVArr==null)
		{
			IJ.showMessage("Error",formula + " Bad Formula, Element or count missing");
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
		if(gPlotWindow==null || gPlotWindow.isClosed())
		{
			Plot plot = new Plot(plotTitle,"MeV","cm2/gm");
			plot.setFont(Font.BOLD, 16);

			//plot.setFont(Font.BOLD, 10);
			plot.setLimits(meVArr[0], meVArr[meVArr.length-1], muMassMin, muMassMax);

			//add the spectra to the plot
			//the circles are used to show the positions of the tabulated NIST data
			//from mmc.getMevArray(formula,minMev,maxMev);
			cnt=0;
			for(i=0;i<muMassSelections.length;i++)
			{
				if(muMassSelections[i])
				{
					plot.setColor(myColors[i]);
					plot.addPoints(meVArr,muMass[cnt],Plot.CONNECTED_CIRCLES);
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
			plot.addLabel(0, 1, formula);
			gPlotWindow = plot.show();
		}
		else
		{
			//double[] oldPlotLimits = gPlotWindow.getPlot().getLimits();

			Plot newPlot = new Plot(plotTitle,"MeV","cm2/gm");
			newPlot.setFont(Font.BOLD, 16);
			//add the spectra to the plot
			//the circles are used to show the positions of the tabulated NIST data
			cnt=0;
			for(i=0;i<muMassSelections.length;i++)
			{
				if(muMassSelections[i])
				{
					newPlot.setColor(myColors[i]);
					newPlot.addPoints(meVArr,muMass[cnt],Plot.CONNECTED_CIRCLES);
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
			newPlot.setLimits(meVArr[0], meVArr[meVArr.length-1], muMassMin, muMassMax);
			newPlot.setColor(Color.BLACK);
			newPlot.addLabel(0, 1, formula);

			gPlotWindow.drawPlot(newPlot);
		}
		
		//Report edges+++++++++++++++++++++++++++++++++++++++++++++++++++++
		//The imageJ plot.addLabel methods are not supported by the plot window
		//re-scale and zoom features, i.e. the labels don't move when zoomed
		//so the edge label feature has been dropped.
		if(reportEdges)
		{
			ArrayList<AtomData> fl = mmc.createFormulaList(formula);
			String[] edgeNames = mmc.getabsEdgeNames();		
			ResultsTable rt = ResultsTable.getResultsTable("Absorption Edges(MeV)");
			if(rt==null)
			{
				rt = new ResultsTable();
				rt.setPrecision(4);
			}
			boolean found = false;
			for(i=0;i< fl.size();i++)
			{
				String theAtom = mmc.getAtomSymbol(fl, i);
				String atm = theAtom;
				//Add the atom if not already listed
				for(i=0;i< rt.size();i++)
				{
					if(atm.toUpperCase().equals(rt.getStringValue("Name", i).toUpperCase()))
					{
						found=true;
						break;
					}
				}
				if(found==false)
				{
					rt.incrementCounter();
					rt.addValue("Name",theAtom);
					for(String edge : edgeNames)
					{	
						double edgeMev = mmc.getAtomAbsEdge(theAtom ,edge ) ;
						if(edgeMev > 0)
						{
							//rt.addValue(edge, ResultsTable.d2s(edgeMev, 5));
							rt.addValue(edge, edgeMev);
						}
					}
				}
			}
			rt.show("Absorption Edges(MeV)");
		}

	}
	
	//*********************************************************************************/

	public void actionPerformed(ActionEvent theEvent)
	{
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

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent theEvent)
	{
		GetSelections(gd);

		return true;
	}
}
