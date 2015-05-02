package edu.berkeley.ce.rockslicing

import scala.collection.mutable.ListBuffer
import java.io._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf

object RockSlicer {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("CS 267 Final Project")
    val sc = new SparkContext(conf)

    /* Open and read input file, producing list of linear inequalities specifying region and
     * list of linear equalities specifying discontinuities
     */
    var rockBuffer = new ListBuffer[Face]()
    var jointBuffer = new ListBuffer[Joint]()
    val inputFile = "tester" // Input file name
    inputProcessor.readInput(inputFile, rockBuffer, jointBuffer)

    val rockVolume = rockBuffer.toList
    val jointList = jointBuffer.toList

    println(rockVolume mkString "\n") // Print input to verify during debugging
    println(jointBuffer mkString "\n")
    println("Input has been processed")

    // FIXME Is this correct instantiation of our first block?
    val blocks = List(Block((0.0, 0.0, 0.0), rockVolume))
    // Generate a list of initial blocks and change it into an RDD
    val blockRdd = sc.parallelize(blocks)

    /*
     * Iterate through the discontinuities, cutting blocks where appropriate and producing
     * a new list of blocks at each step
     */
    val broadcastJoints = sc.broadcast(jointList)
    var cutBlocks = blockRdd
    for (joint <- broadcastJoints.value) {
      cutBlocks = cutBlocks.flatMap(_.cut(joint))
    }

    // Remove redundant joints as described in the original paper
    val nonRedundantBlocks = cutBlocks.map { case block @ Block(center, _) =>
                                             Block(center, block.nonRedundantFaces) }

    // Convert the list of rock blocks to JSON and save this to an output file
    val finalBlocks = nonRedundantBlocks.collect()
    val blockJson = json.rockBlocksToReadableJson(finalBlocks)
    val outputFile = "blocks.json"
    val writer = new BufferedWriter(new FileWriter(outputFile))
    try {
      writer.write(blockJson)
    } catch {
        case e: Exception => {
          println(s"Error writing to file: $e")
        }
    } finally {
      writer.close()
    }
  }
}
