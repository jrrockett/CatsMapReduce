package com.mapreduce

import java.io.{BufferedReader, File, FileOutputStream}

import cats.effect.IO
import cats.implicits._

import Utils.{fileReader, fileWriter}

object Shard {
  private val basePath = "resources/shards/"
  /**
   * Shard the input file to be used by n workers for map and reduce.
   * Copy file line by line to intermediate files
   *
   * TODO: Implement with fs2 when partial word writing issue is fixed
   */
  def shard(inFileName: String, nWorkers: Int): IO[List[String]] = {
    val shardLength = math.ceil(new File(inFileName).length.toFloat / nWorkers.toFloat).toInt
    val inFile = new File(inFileName)

    def writeShard(br: BufferedReader, fos: FileOutputStream, acc: Long, bytesToRead: Long): IO[Long] =
      for {
        line <- IO(br.readLine)
        tot <-
          if (line == null)
            IO.pure(acc)
          else if (acc + line.getBytes.length >= bytesToRead) {
            IO(fos.write(line.getBytes)) >>
            IO.pure(acc + line.getBytes.length)
          } else {
            IO(fos.write(line.getBytes)) >>
            IO(fos.write("\n".getBytes)) >>
            writeShard(br, fos, acc + line.getBytes.length + "\n".getBytes.length, bytesToRead)
          }
      } yield tot

    def split(br: BufferedReader, shardCount: Int, bytesRead: Long, bytesToRead: Long, newBytesRead: Long, shardNames: List[String]): IO[List[String]] = {
      if (bytesRead >= bytesToRead) IO(shardNames)
      else {
        val shardName = basePath + "shard" + shardCount.toString
        val outFile = new File(shardName)
        fileWriter(outFile)
          .use(fos => writeShard(br, fos, 0, shardLength)
            >>= (n => {
            if (n == 0) IO(shardNames)
            else split(br, shardCount + 1, bytesRead + n, bytesToRead, n, shardNames.prepended(shardName))
          }))
      }
    }

    fileReader(inFile).use(br => split(br, 0, 0, inFile.length, -1, List[String]()))
  }
}