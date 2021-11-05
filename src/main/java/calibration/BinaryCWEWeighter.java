/**
 * MIT License
 * Copyright (c) 2019 Montana State University Software Engineering Labs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package calibration;

import org.apache.commons.lang3.ArrayUtils;
import pique.calibration.IWeighter;
import pique.calibration.WeightResult;
import pique.model.ModelNode;
import pique.model.QualityModel;
import pique.utility.BigDecimalWithContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

/** 
 * @author Andrew Johnson
 *
 * This class should weight based off of pairwise comparisons for the quality aspect level but utilize manual weights for 
 * product factors to the quality aspect level. This allows stakeholder
 * interest to be represented but reduces the extreme amount of comparisons.
 */
public class BinaryCWEWeighter implements IWeighter{
	private String[] qaNames;
	private String[] pfNames;
	private BigDecimalWithContext[][] manWeights;
	private BigDecimalWithContext[][] comparisonMat;
	private int numQA;
	private int numPF;
	

	/**
	 * This class will take a quality model as input and will create the weighting for edges from each layer to the next. 
	 * This weighter uses averages to weight edges into each node up to the product factor level, then uses manual weighting
	 * as specified by the .csv comparisons for Product Factor to Quality Aspects, then uses that same file to find comparisons 
	 * for weighting the Quality Aspects to the TQI using the AHP.
	 * 
	 * @param qualityModel The QualityModel to instantiate weights for
	 * @param externalInput Used to pass in path to comparison matrix in the case of non-standard location, for example when testing 
	 */
	@Override
	public Set<WeightResult> elicitateWeights(QualityModel qualityModel, Path... externalInput) {
		numQA = qualityModel.getQualityAspects().size();
		numPF = qualityModel.getProductFactors().size();
		qaNames = new String[numQA];
		pfNames = new String[numPF];
		manWeights = new BigDecimalWithContext[numPF][numQA];
		comparisonMat = new BigDecimalWithContext[numQA][numQA];
		
		List<String> modelQANames = new ArrayList<String>();
		List<String> modelPFNames = new ArrayList<String>();
		qualityModel.getQualityAspects().values().stream().forEach(s -> modelQANames.add(s.getName()));
		qualityModel.getProductFactors().values().stream().forEach(s -> modelPFNames.add(s.getName()));
		
		
		
		String pathToCsv = "src/main/resources/comparisons.csv";
		if (externalInput.length>0) pathToCsv = externalInput[0].toString();
		
		String pfPrefix = "Category ";
		BufferedReader csvReader;
		int lineCount = 0;
		try {
			csvReader = new BufferedReader(new FileReader(pathToCsv));
		
			String row;
			while (( row = csvReader.readLine()) != null) {
				String[] data = row.split(",");
				//first line is qaNames
				if (lineCount == 0) {
					for (int i = 1; i < data.length; i++) {
						qaNames[i-1] = data[i];
					}
					lineCount++;
				}
				//otherwise, check if the first entry of data is part of the model
				else if (modelQANames.contains(data[0]) || modelPFNames.contains(getCategoryName(data[0],pfPrefix))) {
					if (lineCount < numQA+1) { //tqi weights, fill values for ahpMat
						for (int i = 1; i < data.length; i++) {
							comparisonMat[lineCount-1][i-1] = new BigDecimalWithContext(Double.parseDouble(data[i].trim()));
						}
					}
					else { //QA weights, fill values for manWeights
						//parse out the integer for the CWE number and add appropriate prefix, unless it is not numbered
						pfNames[lineCount-numQA-1] = getCategoryName(data[0],pfPrefix);
						for (int i = 1; i < data.length; i++) {							
							manWeights[lineCount-numQA-1][i-1] = new BigDecimalWithContext(Double.parseDouble(data[i].trim()));
						}
					}
				    lineCount++;
				}
			}
			csvReader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		Set<WeightResult> weights = new HashSet<>();

		//there is certainly a better way to implement this next part
		
		//set the weights as a simple 1/number of children for each edge
		averageWeights(qualityModel.getMeasures().values(),weights);
		averageWeights(qualityModel.getDiagnostics().values(),weights);
		averageWeights(qualityModel.getProductFactors().values(),weights);


		//set the weights for edges going into quality aspects based on manual weighting
		manualWeights(qualityModel.getQualityAspects().values(),weights);
		
		//set the weights for edges going into tqi based on ahp
		ahpWeights(qualityModel.getTqi(), weights);
		
        return weights;
	}
	
	




	@Override
	public String getName() {
        return this.getClass().getCanonicalName();
	}

	/**
	 * This class will take a name and prefix and add the prefix to the name, as well as "CWE-". This is to convert the node name from the comparison
	 * file to the specific strings that are used in the quality model as of right now. This could easily break if the file or quality model changes.
	 * @param name The name of the model node as given in the comparison file. 
	 * @param pfPrefix The common prefix for all categories
	 * @return The Category in the model based upon the given name.
	 */
	private String getCategoryName(String name, String pfPrefix) {
		if (name.replaceAll("[\\D]", "").length() == 0) {
			//no numbers in the name
			return pfPrefix +name;
		}
		else {
			return pfPrefix + "CWE-" + Integer.toString(Integer.parseInt(name.replaceAll("[\\D]", "")));							
		}
	}
	
	/**
	 * Sets the incoming weights for a node to evaluate the average value of children. 
	 * @param values Nodes to set weights for
	 * @param weights A set to keep track of weights
	 */
	private void averageWeights(Collection<ModelNode> values, Set<WeightResult> weights) {
		values.forEach(node -> {
			WeightResult weightResult = new WeightResult(node.getName());
			node.getChildren().values().forEach(child -> weightResult.setWeight(child.getName(), averageWeight(node)));
			weights.add(weightResult);
		});
	}

	/**
	 * Calculates 1/(number of children) for the current node.
	 * @param currentNode 
	 * @return 1/currentNode.numberOfChildren
	 */
	private BigDecimal averageWeight(ModelNode currentNode) {
        return new BigDecimalWithContext(1.0).divide(
        		new BigDecimalWithContext(currentNode.getChildren().size()),
        		BigDecimalWithContext.getMC());
    }
	

	/**
	 * This sets the incoming weights for a node based on AHP.
	 * @param tqi The TQI node of the model, or the node for which to calculate the incoming edges. 
	 * @param weights A set to keep track of weights.
	 */
	private void ahpWeights(ModelNode tqi, Set<WeightResult> weights) {
		BigDecimal[] ahpVec = new BigDecimal[numQA];
		//normalize by column sums
		BigDecimal[][] norm = normalizeByColSum(comparisonMat);
		//get the row means
		for (int i= 0; i < numQA; i++) {
			ahpVec[i] = rowMean(norm,i);
		}
		WeightResult weightResult = new WeightResult(tqi.getName());	
		tqi.getChildren().values().forEach(child -> 
			weightResult.setWeight(child.getName(), ahpVec[ArrayUtils.indexOf(qaNames, child.getName())]));
        weights.add(weightResult);
	}
	
	/**
	 * Normalize a 2d array by column sum such that every value is that value divided by the sum of the column.
	 */
	private BigDecimal[][] normalizeByColSum(BigDecimal[][] mat) {
		BigDecimal[][] norm = new BigDecimal[mat.length][mat[0].length];
		for (int i= 0; i < mat.length; i++) {
			for (int j= 0; j < mat[0].length; j++) {
				norm[i][j] = (mat[i][j].divide(colSum(mat,j),BigDecimalWithContext.getMC()));
			}	
		}
		return norm;
	}
	
	/**
	 * Normalize a 2d array by row sum such that every value is that value divided by the sum of the row.
	 */
	private BigDecimal[][] normalizeByRowSum(BigDecimal[][] mat) {
		BigDecimal[][] norm = new BigDecimal[mat.length][mat[0].length];
		for (int i= 0; i < mat.length; i++) {
			for (int j= 0; j < mat[0].length; j++) {
				norm[i][j] = (mat[i][j].divide(rowSum(mat,i),BigDecimalWithContext.getMC()));
			}	
		}
		return norm;
	}

	private BigDecimal rowMean(BigDecimal[][] mat, int row) {
		int cols = mat[0].length;  
		return (rowSum(mat,row)).divide(new BigDecimalWithContext(cols), BigDecimalWithContext.getMC());
	}
	
	private BigDecimal rowSum(BigDecimal[][] mat, int row) {
		int cols = mat[0].length;  
		BigDecimal sumRow = new BigDecimalWithContext(0);  
        for(int j = 0; j < cols; j++){  
          sumRow = sumRow.add(mat[row][j]);  
        }
        return sumRow;
	}
	
	private BigDecimal colSum(BigDecimal[][] mat, int col) {
		 int rows = mat.length;  
		 BigDecimal sumCol = new BigDecimalWithContext(0);  
         for(int j = 0; j < rows; j++){  
           sumCol = sumCol.add(mat[j][col]);  
         } 
		return (sumCol);
	}
	
	/**
	 * Weight edges based on manual decisions from the comparisons
	 * @param nodes nodes to set weights for
	 * @param weights Set to keep track of weights
	 */
	private void manualWeights(Collection<ModelNode> nodes, Set<WeightResult> weights) {
		BigDecimal[][] normMat = normalizeByColSum(manWeights);
		

		for (ModelNode node : nodes) {
			WeightResult weightResult = new WeightResult(node.getName());	
			node.getChildren().values().forEach(child -> 
				weightResult.setWeight(child.getName(), normMat[ArrayUtils.indexOf(pfNames, child.getName())][ArrayUtils.indexOf(qaNames, node.getName())]));
	        weights.add(weightResult);
		}
	}
	
}
