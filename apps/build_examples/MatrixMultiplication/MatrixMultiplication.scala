import org.apache.spark.{Partitioner,HashPartitioner}
import org.apache.spark.{SparkContext,SparkConf}

class IndexHashPartitioner(partitions: Int) extends Partitioner {
  def numPartitions: Int = partitions

  def nonNegativeMod(x: Int, mod: Int): Int = {
    val rawMod = x % mod
    rawMod + (if (rawMod < 0) mod else 0)
  }

  def getPartition(key: Any): Int = key match {
    case null => 0
    case two:(_,_) => {
    	val index=(two._1,two._2)
    	nonNegativeMod(index.hashCode, numPartitions)
    }
    case three:(_,_,_)=>{
	val index=(three._1,three._2)
	nonNegativeMod(index.hashCode, numPartitions)
    }
    case _ => 0
  }

  override def equals(other: Any): Boolean = other match {
    case h: IndexHashPartitioner =>
      h.numPartitions == numPartitions
    case _ =>
      false
  }

  override def hashCode: Int = numPartitions
}

object MatrixMultiplication{
	def main(args:Array[String]){
        if (args.length == 0){
                System.err.println("Usage: MatrixMultiplication <master> <MatrixA> <MatrixB> <k>")
                System.exit(1)
        }

        val inputA=args(0)
        val inputB=args(1)
        val k=args(2).toInt
        val outputPath=args(3)
        val isPartitioned=args(4).toBoolean
        
        /**
        val inputA="/a600"
        val inputB="/b600"
        val k=600
        val outputPath="/matrixOut600"
        val isPartitioned=false
        */

        val conf=new SparkConf().setAppName("MatrixMultiplication")
        val sc=new SparkContext(conf)

        val a=sc.textFile(inputA)
        val b=sc.textFile(inputB)

        val a1=a.flatMap(entry => {
                val e=entry.split(" ")
                val i=e(0).toInt
                val j=e(1).toInt
                val v=e(2).toInt
                for {p <-1 until k+1} yield ((i,p,j),v)
        })

        val b1=b.flatMap(entry => {
                val e=entry.split(" ")
                val i=e(0).toInt
                val j=e(1).toInt
                val v=e(2).toInt
                for {p <-1 until k+1} yield ((p,j,i),v)
        })
        
        val indexPartitioner=if(isPartitioned){
        	new IndexHashPartitioner(16)
        }else{
        	new HashPartitioner(16)
        }

        val c1=a1.union(b1)
        val c2=c1.reduceByKey(indexPartitioner,(a,b) => a*b)

        val c3=c2.map(entry=>{
                val key=entry._1
                val v=entry._2
                ((key._1,key._2),v)
        })
        val c4=c3.reduceByKey(indexPartitioner,(a,b) => a+b)

        c4.saveAsTextFile(outputPath)
	}
}
