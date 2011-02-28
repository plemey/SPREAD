// "/home/filip/Dropbox/Phyleography/data/WNX/WNV_relaxed_geo_gamma.trees"
//						System.out.println("Parent time: "
//								+ formatter.format(parentTime));
//
//						System.out.println("Slice time: "
//								+ formatter.format(sliceTime));
//
//						System.out.println("Node time: "
//								+ formatter.format(nodeTime));
//
//						System.out.println("location: " + latitude + " "
//								+ longitude);
//						System.out.println("parent location: " + parentLatitude
//								+ " " + parentLongitude);
//						System.out
//								.println("imputed location: "
//										+ imputedLocation[0] + " "
//										+ imputedLocation[1]);
//
//						System.out.println();
package templates;

import java.awt.Color;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jebl.evolution.graphs.Node;
import jebl.evolution.io.NexusImporter;
import jebl.evolution.io.TreeImporter;
import jebl.evolution.trees.RootedTree;
import math.MultivariateNormalDistribution;
import structure.Coordinates;
import structure.Layer;
import structure.Polygon;
import structure.Style;
import structure.TimeLine;
import utils.SpreadDate;
import utils.Utils;

public class TimeSlicer {

	public static long time;

	private static final int DayInMillis = 86400000;

	private static RootedTree tree;
	private static TreeImporter treesImporter;
	private static TreeImporter treeImporter;
	private static double treeRootHeight;
	private static String precisionString;
	private static String locationString;
	private static String rateString;
	private static int numberOfIntervals;
	private static boolean trueNoise;
	private static String mrsdString;
	private static double timescaler;
	private static TimeLine timeLine;
	private static HashMap<Double, List<Coordinates>> sliceMap;
	private static double sliceTimeMax;
	private static double sliceTimeMin;
	
	
	private enum timescalerEnum {
		DAYS, MONTHS, YEARS
	}

	private static timescalerEnum timescalerSwitcher;

	private static SimpleDateFormat formatter;

	public static void main(String args[]) throws Exception {

		formatter = new SimpleDateFormat("yyyy-MM-dd G", Locale.US);

		sliceTimeMin = Double.MAX_VALUE;
		sliceTimeMax = -Double.MAX_VALUE;
		
		// start timing
		time = -System.currentTimeMillis();

		// this will be parsed from gui
		treesImporter = new NexusImporter(new FileReader(
				"/home/filip/Dropbox/Phyleography/data/WNX/WNX_small.trees"));

		treeImporter = new NexusImporter(
				new FileReader(
						"/home/filip/Dropbox/Phyleography/data/WNX/WNV_relaxed_geo_gamma_MCC.tre"));

		precisionString = "precision";
		locationString = "location";
		rateString = "rate";
		numberOfIntervals = 10;
		trueNoise = false;
		mrsdString = "2011-02-25";

		// parse combobox choices here
		timescalerSwitcher = timescalerEnum.YEARS;

		// this is to choose the proper time scale
		timescaler = Double.NaN;
		switch (timescalerSwitcher) {
		case DAYS:
			timescaler = 1;
			break;
		case MONTHS:
			timescaler = 30;
		case YEARS:
			timescaler = 365;
			break;
		}

		tree = (RootedTree) treeImporter.importNextTree();

		// this is for time calculations
		treeRootHeight = tree.getHeight(tree.getRootNode());

		// This is a general time span for all of the trees
		SpreadDate mrsd = new SpreadDate(mrsdString);
		timeLine = new TimeLine(mrsd.getTime()
				- (treeRootHeight * DayInMillis * timescaler), mrsd.getTime(),
				numberOfIntervals);


		sliceMap = new HashMap<Double, List<Coordinates>>();
		while (treesImporter.hasTree()) {

			RootedTree currentTree = (RootedTree) treesImporter
					.importNextTree();

			// TODO: start separate thread for each tree
			analyzeTree(currentTree, true);

		}// END trees loop
		
		// ////////////////
		// ---POLYGONS---//
		// ////////////////
		
		Set<Double> HostKeys = sliceMap.keySet();
		Iterator<Double> iterator = HostKeys.iterator();
		
		String polygonsDescription = null;
		Layer polygonsLayer = new Layer("Polygons", polygonsDescription);
		
		int polygonsStyleId = 1;
		while (iterator.hasNext()) {
		
		Double sliceTime = (Double) iterator.next();
		
		int red = 55;
		int green = (int) Utils.map(sliceTimeMin, 0, sliceTimeMax, 255, 0);
		int blue = 0;
		int alpha = (int) Utils.map(sliceTimeMin, 0, sliceTimeMax, 100, 255);
		
		Color col = new Color(red, green, blue, alpha);
		Style polygonsStyle = new Style(col, 0);
		polygonsStyle.setId("polygon_style" + polygonsStyleId);

		polygonsLayer.addItem(new Polygon(
				"node" + formatter.format(sliceTime), // String name
				sliceMap.get(sliceTime), // List<Coordinates>
				polygonsStyle, // Style style
				sliceTime, // double startime
				0.0 // double duration
				));

		polygonsStyleId++;
		
		}
		
		// stop timing
		time += System.currentTimeMillis();
		System.out.println("Finished in: " + time + " msec");
	}// END: main

	private static void analyzeTree(RootedTree tree, boolean impute)
			throws ParseException {

		double startTime = timeLine.getStartTime();
		double endTime = timeLine.getEndTime();
		double timeSpan = startTime - endTime;
		double speed = 1;

		double[][] precision = null;

		// TODO: move this to imputeValue
		if (impute) {

			Object o = tree.getAttribute(precisionString);
			double treeNormalization = tree.getHeight(tree.getRootNode());

			Object[] array = (Object[]) o;
			int dim = (int) Math.sqrt(1 + 8 * array.length) / 2;
			precision = new double[dim][dim];
			int c = 0;
			for (int i = 0; i < dim; i++) {
				for (int j = i; j < dim; j++) {
					precision[j][i] = precision[i][j] = ((Double) array[c++])
							* treeNormalization;
				}
			}
		}

		for (Node node : tree.getNodes()) {

			if (!tree.isRoot(node)) {

				for (int i = numberOfIntervals; i > 0; i--) {

					Node parentNode = tree.getParent(node);

					double nodeHeight = tree.getHeight(node);
					double parentHeight = tree.getHeight(parentNode);

					Object[] location = (Object[]) Utils.getArrayNodeAttribute(
							node, locationString);
					double latitude = (Double) location[0];
					double longitude = (Double) location[1];

					Object[] parentLocation = (Object[]) Utils
							.getArrayNodeAttribute(parentNode, locationString);
					double parentLatitude = (Double) parentLocation[0];
					double parentLongitude = (Double) parentLocation[1];

					double rate = Utils
							.getDoubleNodeAttribute(node, rateString);

					double sliceTime = startTime
							- (timeSpan / numberOfIntervals)
							* ((double) i * speed);

					if (sliceTime < sliceTimeMin) {
						sliceTimeMin = sliceTime;
					}
					
					if (sliceTime > sliceTimeMax) {
						sliceTimeMax = sliceTime;
					}
					
					SpreadDate mrsd0 = new SpreadDate(mrsdString);
					double parentTime = mrsd0
							.minus((int) (parentHeight * timescaler));

					SpreadDate mrsd1 = new SpreadDate(mrsdString);
					double nodeTime = mrsd1
							.minus((int) (nodeHeight * timescaler));

					Object[] imputedLocation = imputeValue(location,
							parentLocation, sliceTime, nodeTime, parentTime,
							precision, rate, trueNoise);

					if (parentTime < sliceTime && sliceTime <= nodeTime) {

						
						if(sliceMap.containsKey(sliceTime)) {
							
							sliceMap.get(sliceTime).add(new Coordinates(parentLongitude, parentLatitude, 0.0));
							sliceMap.get(sliceTime).add(new Coordinates(Double.valueOf(imputedLocation[1].toString()), Double.valueOf(imputedLocation[0].toString()), 0.0));
							sliceMap.get(sliceTime).add(new Coordinates(longitude, latitude, 0.0));
							
						} else {
							
						List<Coordinates> coords = new ArrayList<Coordinates>();
						coords.add(new Coordinates(parentLongitude, parentLatitude, 0.0));
						coords.add(new Coordinates(Double.valueOf(imputedLocation[1].toString()), Double.valueOf(imputedLocation[0].toString()), 0.0));
						coords.add(new Coordinates(longitude, latitude, 0.0));
						sliceMap.put(sliceTime, coords);
					
						}// END: key check
						
					}

				}// END: numberOfIntervals loop
				
			}
		}// END: node loop

//		System.out.println("============================================");


	}// END: analyzeTree

	private static Object[] imputeValue(Object[] location,
			Object[] parentLocation, double sliceTime, double nodeTime,
			double parentTime, double[][] precision, double rate,
			boolean trueNoise) {

		int dim = location.length;
		double[] nodeValue = new double[2];
		double[] parentValue = new double[2];

		for (int i = 0; i < dim; i++) {

			nodeValue[i] = Double.parseDouble(location[i].toString());
			parentValue[i] = Double.parseDouble(parentLocation[i].toString());

		}

		final double scaledTimeChild = (sliceTime - nodeTime) * rate;
		final double scaledTimeParent = (parentTime - sliceTime) * rate;
		final double scaledWeightTotal = 1.0 / scaledTimeChild + 1.0
				/ scaledTimeParent;

		if (scaledTimeChild == 0)
			return location;

		if (scaledTimeParent == 0)
			return parentLocation;

		// Find mean value, weighted average
		double[] mean = new double[dim];
		double[][] scaledPrecision = new double[dim][dim];

		for (int i = 0; i < dim; i++) {
			mean[i] = (nodeValue[i] / scaledTimeChild + parentValue[i]
					/ scaledTimeParent)
					/ scaledWeightTotal;

			if (trueNoise) {
				for (int j = i; j < dim; j++)
					scaledPrecision[j][i] = scaledPrecision[i][j] = precision[i][j]
							* scaledWeightTotal;
			}
		}

		if (trueNoise) {
			mean = MultivariateNormalDistribution
					.nextMultivariateNormalPrecision(mean, scaledPrecision);
		}

		Object[] result = new Object[dim];
		for (int i = 0; i < dim; i++)
			result[i] = mean[i];

		return result;
	}// END ImputeValue

}// END: TimeSlicer class
