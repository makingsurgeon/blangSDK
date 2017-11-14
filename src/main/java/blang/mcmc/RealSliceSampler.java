package blang.mcmc;

import java.util.List;
import bayonet.distributions.Random;
import bayonet.math.NumericalUtils;
import blang.core.LogScaleFactor;
import blang.core.WritableRealVar;
import blang.distributions.Generators;
import blang.mcmc.internals.SamplerBuilderContext;


public class RealSliceSampler implements Sampler
{
  @SampledVariable
  protected WritableRealVar variable;
  
  @ConnectedFactor
  protected List<LogScaleFactor> numericFactors;
  
  private final double fixedWindowLeft, fixedWindowRight;
  
  public boolean useFixedWindow()
  {
    return !Double.isNaN(fixedWindowLeft);
  }
  
  private static final double initialWindowSize = 1.0;
  
  private RealSliceSampler(double fixedWindowLeft, double fixedWindowRight) 
  {
    this.fixedWindowLeft = fixedWindowLeft;
    this.fixedWindowRight = fixedWindowRight;
  }
  
  private RealSliceSampler()
  {
    this(Double.NaN, Double.NaN);
  }
  
  public static RealSliceSampler build(WritableRealVar variable, List<LogScaleFactor> numericFactors)
  {
    return build(variable, numericFactors, Double.NaN, Double.NaN);
  }
  
  public static RealSliceSampler build(WritableRealVar variable, List<LogScaleFactor> numericFactors, double fixedWindowLeft, double fixedWindowRight)
  {
    RealSliceSampler result = new RealSliceSampler(fixedWindowLeft, fixedWindowRight);
    result.variable = variable;
    result.numericFactors = numericFactors;
    return result;
  }
  
  public void execute(Random random)
  {
    // sample slice
    final double logSliceHeight = nextSliceHeight(random); // log(Y) in Neal's paper
    final double oldState = variable.doubleValue();        // x0 in Neal's paper
   
    // doubling procedure
    double leftProposalEndPoint, rightProposalEndPoint;
    
    if (useFixedWindow())
    {
      leftProposalEndPoint = fixedWindowLeft;
      rightProposalEndPoint = fixedWindowRight;
    }
    else
    {
      leftProposalEndPoint = oldState - initialWindowSize * random.nextDouble(); // L in Neal's paper
      rightProposalEndPoint = leftProposalEndPoint + initialWindowSize;          // R in Neal's paper
      
      while (logSliceHeight < logDensityAt(leftProposalEndPoint) || logSliceHeight < logDensityAt(rightProposalEndPoint)) 
        if (random.nextBernoulli(0.5))
        {
          leftProposalEndPoint += - (rightProposalEndPoint - leftProposalEndPoint);
          // note 1: check we don't diverge to INF 
          // this as that can arise e.g. when encountering an improper posterior
          // avoid infinite loop then and warn user.
          if (leftProposalEndPoint == Double.NEGATIVE_INFINITY)
            throw new RuntimeException(INFINITE_SLICE_MESSAGE);
        }
        else
        {
          rightProposalEndPoint += rightProposalEndPoint - leftProposalEndPoint;
          // same as note 1 above
          if (rightProposalEndPoint == Double.POSITIVE_INFINITY)
            throw new RuntimeException(INFINITE_SLICE_MESSAGE);
        }
    }
    
    // shrinkage procedure
    double 
      leftShrankEndPoint = leftProposalEndPoint,   // bar L in Neal's paper
      rightShrankEndPoint = rightProposalEndPoint; // bar R in Neal's paper
    while (true) 
    {
      final double newState = Generators.uniform(random, leftShrankEndPoint, rightShrankEndPoint); // x1 in Neal's paper
      if (logSliceHeight <= logDensityAt(newState) && accept(oldState, newState, logSliceHeight, leftProposalEndPoint, rightProposalEndPoint))
      {
        variable.set(newState);
        return;
      }
      if (newState < oldState)
        leftShrankEndPoint = newState;
      else 
        rightShrankEndPoint = newState;
      if (NumericalUtils.isClose(leftShrankEndPoint, rightShrankEndPoint, 1e-6))
      {
        // If a conditional is degenerate, the shrinkage will be relatively slow,
        // but worse, depending on how rounding behaves, may miss the oldState. 
        // This was observed in a case caused by the uniform generator excluding
        // the right end point, creating an infinite loop.
        variable.set(oldState);
        return;
      }
    }
  }
  
  private boolean accept(double oldState, double newState, double logSliceHeight, double leftProposalEndPoint, double rightProposalEndPoint) 
  {
    if (useFixedWindow())
      return true;
    
    boolean differ = false; // D in Neal's paper; whether the intervals generated by new and old differ; used for optimization
    while (rightProposalEndPoint - leftProposalEndPoint > 1.1 * initialWindowSize) // 1.1 factor to cover for numerical round offs
    {
      final double middle = (leftProposalEndPoint + rightProposalEndPoint) / 2.0; // M in Neal's paper
      if ((oldState <  middle && newState >= middle) || 
          (oldState >= middle && newState < middle))
        differ = true;
      if (newState < middle)
        rightProposalEndPoint = middle;
      else
        leftProposalEndPoint = middle;
      if (differ && logSliceHeight >= logDensityAt(leftProposalEndPoint) && logSliceHeight >= logDensityAt(rightProposalEndPoint))
        return false;
    }
    return true;
  }

  private double nextSliceHeight(Random random)
  {
    return logDensity() - Generators.unitRateExponential(random); 
  }
  
  private double logDensityAt(double x)
  {
    variable.set(x);
    return logDensity();
  }
  
  private double logDensity() {
    double sum = 0.0;
    for (LogScaleFactor f : numericFactors)
      sum += f.logDensity();
    return sum;
  }
  
  public boolean setup(SamplerBuilderContext context) 
  {
    return true;
  }
  
  public static final String INFINITE_SLICE_MESSAGE = "Slice diverged to infinity. "
      + "Possible cause is that a variable has no distribution attached to it, i.e. the model is improper.";
}