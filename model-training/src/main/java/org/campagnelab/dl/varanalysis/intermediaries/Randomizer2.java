package org.campagnelab.dl.varanalysis.intermediaries;


import com.codahale.metrics.MetricRegistryListener;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XorShift128PlusRandom;
import org.apache.commons.io.FileUtils;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.dl.varanalysis.storage.RecordReader;
import org.campagnelab.dl.varanalysis.storage.RecordWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 *
 *
 * the randomizer object iterates over a parquet file and randomizes the order of records in batches
 *
 * Created by rct66 on 5/18/16.
 * @author rct66
 */
public class Randomizer2 extends Intermediary{
    public static final int BUCKET_SIZE = 20000;
    static private Logger LOG = LoggerFactory.getLogger(Randomizer2.class);


    public static void main (String[] args) throws IOException{

        //randomize
        new Randomizer2().executeOver(args[0],args[1]);
    }

    public void execute(String inPath, String outPath, int blockSize, int pageSize) throws IOException {
        String workingDir = new File(outPath).getParent();
        try {
            RecordReader allReader = new RecordReader(inPath);
            int numBuckets = (int)(allReader.getTotalRecords()/BUCKET_SIZE)+1;
            List<RecordWriter> bucketWriters = new ObjectArrayList<RecordWriter>(numBuckets);
            for (int i = 0; i < numBuckets; i++){
                bucketWriters.add(new RecordWriter(workingDir+"/tmp/bucket"+i+".parquet",blockSize,pageSize,true));
            }
            RecordWriter allWriter = new RecordWriter(outPath,blockSize,pageSize,true);
            Random rand = new XorShift128PlusRandom();

            //set up logger
            ProgressLogger pgRead = new ProgressLogger(LOG);
            pgRead.itemsName = "read";
            pgRead.expectedUpdates = allReader.getTotalRecords();
            pgRead.displayFreeMemory = true;
            pgRead.start();

            //fill buckets randomly
            System.out.println("Filling " + numBuckets + " temp buckets randomly");
            for (BaseInformationRecords.BaseInformation rec : allReader) {
                pgRead.update();
                int bucket = rand.nextInt(numBuckets);
                bucketWriters.get(bucket).writeRecord(rec);
            }
            pgRead.stop();
            allReader.close();

            System.out.println("Shuffling contents of each bucket and writing to output file");
            //iterate over buckets
            ProgressLogger pgTempBucket = new ProgressLogger(LOG);
            pgTempBucket.itemsName = "read";
            pgTempBucket.expectedUpdates = numBuckets;
            pgTempBucket.displayFreeMemory = true;
            pgTempBucket.start();
            int i = 0;
            for (RecordWriter bucketWriter : bucketWriters){
                bucketWriter.close();

                //put contents of bucket in a list
                RecordReader bucketReader = new RecordReader(workingDir+"/tmp/bucket"+i+".parquet");
                List<BaseInformationRecords.BaseInformation> records = new ObjectArrayList<>(BUCKET_SIZE);
                for (BaseInformationRecords.BaseInformation rec : bucketReader){
                    records.add(rec);
                }
                bucketReader.close();

                //shuffle list
                Collections.shuffle(records,rand);

                //write list to final file
                for (BaseInformationRecords.BaseInformation rec : records){
                    allWriter.writeRecord(rec);
                }
                i++;
                pgTempBucket.update();
            }
            pgTempBucket.stop();
            allWriter.close();

            //delete temp files
            FileUtils.deleteDirectory(new File((workingDir+"/tmp")));



        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
