package blang.engines;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import bayonet.distributions.Random;
import blang.engines.internals.ladders.EquallySpaced;
import blang.engines.internals.ladders.TemperatureLadder;
import blang.inits.Arg;
import blang.inits.DefaultValue;
import blang.inits.experiments.Cores;
import blang.runtime.SampledModel;
import briefj.BriefParallel;

public class ParallelTempering
{
  @Arg           @DefaultValue("Dynamic")
  public Cores nThreads = Cores.dynamic();  
  
  @Arg                   @DefaultValue("EquallySpaced")
  public TemperatureLadder ladder = new EquallySpaced();
  
  @Arg(description = "If unspecified, use the number of threads.") 
                                  @DefaultValue("8")
  public Optional<Integer> nChains = Optional.of(8);
  
  @Arg              @DefaultValue("true")
  public boolean usePriorSamples = true;
  
  // convention: state index 0 is room temperature (target of interest)
  protected SampledModel [] states;
  protected List<Double> temperingParameters;
  private Random [] parallelRandomStreams;
  protected SummaryStatistics [] swapAcceptPrs;
  private int iterationIndex = 0;
  
  public SampledModel getTargetState()
  {
    if (states[0].getExponent() != 1.0)
      throw new RuntimeException();
    return states[0];
  }
  
  public void swapKernel()
  {
    int offset = iterationIndex++ % 2;
    BriefParallel.process((nChains() - offset) / 2, nThreads.numberAvailable(), swapIndex ->
    {
      int chainIndex = offset + 2 * swapIndex;
      double acceptPr = swapKernel(parallelRandomStreams[chainIndex], chainIndex);
      swapAcceptPrs[chainIndex].addValue(acceptPr);
    });
  }
  
  public void moveKernel(int nPasses) 
  {
    BriefParallel.process(nChains(), nThreads.numberAvailable(), chainIndex -> 
    {
      Random random = parallelRandomStreams[chainIndex];
      SampledModel current = states[chainIndex];
      if (temperingParameters.get(chainIndex) == 0 && usePriorSamples)
        current.forwardSample(random, false);
      else
        for (int i = 0; i < nPasses; i++)
          current.posteriorSamplingScan(random); 
    });
  }
  
  /**
   * @param random
   * @param i one of the indices to swap, the other being i+1
   * @return Accept pr
   */
  public double swapKernel(Random random, int i)
  {
    int j = i + 1;
    double logRatio = 
        states[i].logDensity(temperingParameters.get(j)) + states[j].logDensity(temperingParameters.get(i))
      - states[i].logDensity(temperingParameters.get(i)) - states[j].logDensity(temperingParameters.get(j));
    double acceptPr = Math.min(1.0, Math.exp(logRatio));
    if (Double.isNaN(acceptPr))
      acceptPr = 0.0; // should only happen right at the beginning
    if (random.nextBernoulli(acceptPr))
      doSwap(i);
    return acceptPr;
  }
  
  private void doSwap(int i) 
  {
    int j = i + 1;
    SampledModel tmp = states[i];
    states[i] = states[j];
    states[j] = tmp;
    states[i].setExponent(temperingParameters.get(i));
    states[j].setExponent(temperingParameters.get(j));
  }
  
  public int nChains()
  {
    return states.length;
  }
  
  public void initialize(SampledModel prototype, Random random)
  {
    List<SampledModel> initStates = new ArrayList<>();
    temperingParameters = ladder.temperingParameters(nChains.orElse(nThreads.numberAvailable()));
    if (temperingParameters.get(0) != 1.0)
      throw new RuntimeException();
    System.out.println("Temperatures: " + temperingParameters);
    int nChains = temperingParameters.size();
    states = initStates.isEmpty() ? defaultInit(prototype, nChains, random) : (SampledModel[]) initStates.toArray();
    swapAcceptPrs = new SummaryStatistics[nChains - 1];
    for (int i = 0; i < nChains - 1; i++)
      swapAcceptPrs[i] = new SummaryStatistics();
    parallelRandomStreams =  Random.parallelRandomStreams(random, nChains);
  }
  
  private SampledModel [] defaultInit(SampledModel prototype, int nChains, Random random)
  {
    SampledModel [] result = (SampledModel []) new SampledModel[nChains];
    for (int i = 0; i < nChains; i++)
    {
      result[i] = prototype.duplicate();
      result[i].setExponent(temperingParameters.get(i)); 
    }
    return result;
  }
}
