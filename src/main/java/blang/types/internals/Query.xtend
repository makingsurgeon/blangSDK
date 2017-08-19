package blang.types.internals

import org.eclipse.xtend.lib.annotations.Data
import blang.runtime.objectgraph.SkipDependency
import java.util.LinkedHashSet
import blang.types.Index
import blang.types.Plate
import java.util.Set
import org.eclipse.xtend.lib.annotations.Accessors

/**
 * Utility for HashPlate and HashPlated.
 */
@Data // important! this is used in hash tables
class Query {
  @SkipDependency
  val Set<Index<?>> indices
  def static Query build(Index<?> ... indices) {
    return new Query(new LinkedHashSet(indices))
  }
  def QueryType type() { 
    return new QueryType(indices.map[index | index.plate].toSet)
  }
  @Data
  static class QueryType {
    @Accessors(PUBLIC_GETTER)
    val Set<Plate<?>> plates
  }
}