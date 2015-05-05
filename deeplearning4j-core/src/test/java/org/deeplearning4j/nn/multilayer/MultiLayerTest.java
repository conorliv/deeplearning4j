/*
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.multilayer;

import java.util.Arrays;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.LFWDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.preprocessor.ComposableInputPreProcessor;
import org.deeplearning4j.nn.layers.convolution.ConvolutionLayer;
import org.deeplearning4j.nn.layers.convolution.preprocessor.ConvolutionInputPreProcessor;
import org.deeplearning4j.nn.layers.convolution.subsampling.SubsamplingLayer;
import org.deeplearning4j.nn.layers.feedforward.autoencoder.AutoEncoder;
import org.deeplearning4j.nn.layers.feedforward.rbm.RBM;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.LayerFactory;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.override.ClassifierOverride;
import org.deeplearning4j.nn.conf.override.ConfOverride;
import org.deeplearning4j.nn.layers.OutputLayer;
import org.deeplearning4j.nn.layers.convolution.ConvolutionDownSampleLayer;
import org.deeplearning4j.nn.layers.convolution.preprocessor.ConvolutionPostProcessor;
import org.deeplearning4j.nn.layers.factory.DefaultLayerFactory;
import org.deeplearning4j.nn.layers.factory.LayerFactories;
import org.deeplearning4j.nn.layers.factory.PretrainLayerFactory;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.optimize.stepfunctions.GradientStepFunction;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by agibsonccc on 12/27/14.
 */
public class MultiLayerTest {

    private static final Logger log = LoggerFactory.getLogger(MultiLayerTest.class);

    @Test
    public void testDbnFaces() {
        Nd4j.dtype = DataBuffer.DOUBLE;
        LayerFactory layerFactory = LayerFactories.getFactory(RBM.class);
        DataSetIterator iter = new LFWDataSetIterator(28,28);


        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT)
                .constrainGradientToUnitNorm(true)
                .weightInit(WeightInit.DISTRIBUTION).dist(new NormalDistribution(1,1e-5))
                .iterations(100).learningRate(1e-3)
                .nIn(next.numInputs()).nOut(next.numOutcomes()).visibleUnit(RBM.VisibleUnit.GAUSSIAN).hiddenUnit(RBM.HiddenUnit.RECTIFIED).layerFactory(layerFactory)
                .list(4).hiddenLayerSizes(600,250,100).override(new ConfOverride() {
                    @Override
                    public void overrideLayer(int i, NeuralNetConfiguration.Builder builder) {
                        if (i == 3) {
                            builder.layerFactory(new DefaultLayerFactory(OutputLayer.class));
                            builder.activationFunction("softmax");
                            builder.lossFunction(LossFunctions.LossFunction.MCXENT);

                        }
                    }
                }).build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.fit(next);

    }

    @Test
    public void testConvolutionWithSubSampling() {
        LayerFactory layerFactory = LayerFactories.getFactory(ConvolutionDownSampleLayer.class);
        int batchSize = 110;
        /**
         *
         */
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT)
                .iterations(100).weightInit(WeightInit.VI).stepFunction(new GradientStepFunction())
                .activationFunction("tanh").filterSize(5, 1, 2, 2)
                .nIn(4).nOut(3).batchSize(batchSize)
                .visibleUnit(RBM.VisibleUnit.GAUSSIAN)
                .hiddenUnit(RBM.HiddenUnit.RECTIFIED)
                .layerFactory(layerFactory)
                .list(3)
                .inputPreProcessor(0,new ConvolutionInputPreProcessor(2,2))
                .preProcessor(1, new ConvolutionPostProcessor())
                .hiddenLayerSizes(new int[]{1})
                .override(0, new ConfOverride() {
                    @Override
                    public void overrideLayer(int i, NeuralNetConfiguration.Builder builder) {
                        builder.layerFactory(LayerFactories.getFactory(ConvolutionLayer.class));
                        builder.convolutionType(ConvolutionDownSampleLayer.ConvolutionType.MAX);
                        builder.featureMapSize(2,2);
                    }
                }).override(1,new ConfOverride() {
                    @Override
                    public void overrideLayer(int i, NeuralNetConfiguration.Builder builder) {
                        builder.layerFactory(LayerFactories.getFactory(SubsamplingLayer.class));

                    }
                }).override(2, new ClassifierOverride(2))
                .build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.setIterationListeners(Arrays.<IterationListener>asList(new ScoreIterationListener(10)));
        network.init();
        DataSetIterator iter = new IrisDataSetIterator(150, 150);


        org.nd4j.linalg.dataset.DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        /**
         * Likely cause: shape[0] mis match on the filter size and the input batch size.
         * Likely need to make a little more general.
         */
        network.fit(trainTest.getTrain().getFeatureMatrix().reshape(trainTest.getTrain().numExamples(),1,2,2),trainTest.getTrain().getLabels());


        //org.nd4j.linalg.dataset.DataSet test = trainTest.getTest();
        Evaluation eval = new Evaluation();
        INDArray output = network.output(trainTest.getTrain().getFeatureMatrix().reshape(trainTest.getTrain().numExamples(), 1, 2, 2));
        eval.eval(trainTest.getTrain().getLabels(),output);
        log.info("Score " +eval.stats());


    }


    @Test
    public void testBackPropConvolution() {

        LayerFactory layerFactory = LayerFactories.getFactory(ConvolutionDownSampleLayer.class);
        int batchSize = 110;
        /**
         *
         */
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT)
                .iterations(100).weightInit(WeightInit.VI).stepFunction(new GradientStepFunction())
                .activationFunction("tanh").filterSize(5,1,2,2)
                .nIn(4).nOut(3).batchSize(batchSize)
                .visibleUnit(RBM.VisibleUnit.GAUSSIAN)
                .hiddenUnit(RBM.HiddenUnit.RECTIFIED)
                .layerFactory(layerFactory)
                .list(2).backward(true)
                .preProcessor(0,new ConvolutionPostProcessor())
                .hiddenLayerSizes(new int[]{4})
                .override(1, new ClassifierOverride(1)).build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        DataSetIterator iter = new IrisDataSetIterator(150, 150);


        org.nd4j.linalg.dataset.DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        /**
         * Likely cause: shape[0] mis match on the filter size and the input batch size.
         * Likely need to make a little more general.
         */
        network.fit(trainTest.getTrain().getFeatureMatrix().reshape(trainTest.getTrain().numExamples(),1,2,2),trainTest.getTrain().getLabels());


        //org.nd4j.linalg.dataset.DataSet test = trainTest.getTest();
        Evaluation eval = new Evaluation();
        INDArray output = network.output(trainTest.getTrain().getFeatureMatrix().reshape(trainTest.getTrain().numExamples(), 1, 2, 2));
        eval.eval(trainTest.getTrain().getLabels(),output);
        log.info("Score " +eval.stats());


    }



    @Test
    public void testBackProp() {
        LayerFactory layerFactory = LayerFactories.getFactory(AutoEncoder.class);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT).lossFunction(LossFunctions.LossFunction.RMSE_XENT)
                .iterations(100).weightInit(WeightInit.DISTRIBUTION).momentum(0.5)
                .activationFunction("tanh")
                .dist(new NormalDistribution(1e-1,1e-1))
                .nIn(4).nOut(3).visibleUnit(RBM.VisibleUnit.GAUSSIAN).hiddenUnit(RBM.HiddenUnit.RECTIFIED)
                .layerFactory(layerFactory)
                .list(2).backward(true).pretrain(false)
                .hiddenLayerSizes(new int[]{3}).override(1, new ConfOverride() {
                    @Override
                    public void overrideLayer(int i, NeuralNetConfiguration.Builder builder) {
                        builder.activationFunction("softmax");
                        builder.lossFunction(LossFunctions.LossFunction.MCXENT);
                        builder.weightInit(WeightInit.ZERO);
                        builder.layerFactory(LayerFactories.getFactory(OutputLayer.class));
                    }
                }).build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.setIterationListeners(Arrays.<IterationListener>asList(new ScoreIterationListener(1)));
        DataSetIterator iter = new IrisDataSetIterator(150, 150);


        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        network.setInput(trainTest.getTrain().getFeatureMatrix());
        network.setLabels(trainTest.getTrain().getLabels());
        network.fit(trainTest.getTrain());


        DataSet test = trainTest.getTest();
        Evaluation eval = new Evaluation();
        INDArray output = network.output(test.getFeatureMatrix());
        eval.eval(test.getLabels(),output);
        log.info("Score " +eval.stats());


    }

    @Test
    public void testDbn() throws Exception {
        Nd4j.MAX_SLICES_TO_PRINT = -1;
        Nd4j.MAX_ELEMENTS_PER_SLICE = -1;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .iterations(100).layerFactory(new PretrainLayerFactory(RBM.class))
                .weightInit(WeightInit.DISTRIBUTION).dist(new UniformDistribution(0,1))
                .activationFunction("tanh").momentum(0.9)
                .optimizationAlgo(OptimizationAlgorithm.LBFGS)
                .constrainGradientToUnitNorm(true).k(1).regularization(true).l2(2e-4)
                .visibleUnit(RBM.VisibleUnit.GAUSSIAN).hiddenUnit(RBM.HiddenUnit.RECTIFIED)
                .lossFunction(LossFunctions.LossFunction.RMSE_XENT)
                .nIn(4).nOut(3).list(2)
                .hiddenLayerSizes(new int[]{3})
                .override(1, new ClassifierOverride(1)).build();

            NeuralNetConfiguration conf2 = new NeuralNetConfiguration.Builder()
                    .layerFactory(LayerFactories.getFactory(RBM.class))
                    .nIn(784).nOut(600).applySparsity(true).sparsity(0.1)
                    .build();

        Layer l = LayerFactories.getFactory(RBM.class).create(conf2,
                Arrays.<IterationListener>asList(new ScoreIterationListener(2)));



        MultiLayerNetwork d = new MultiLayerNetwork(conf);


        DataSetIterator iter = new IrisDataSetIterator(150, 150);

        DataSet next = iter.next();

        Nd4j.writeTxt(next.getFeatureMatrix(),"iris.txt","\t");

        next.normalizeZeroMeanZeroUnitVariance();

        SplitTestAndTrain testAndTrain = next.splitTestAndTrain(110);
        DataSet train = testAndTrain.getTrain();

        d.fit(train);




        DataSet test = testAndTrain.getTest();


        Evaluation eval = new Evaluation();
        INDArray output = d.output(test.getFeatureMatrix());
        eval.eval(test.getLabels(),output);
        log.info("Score " + eval.stats());


    }

}
