package org.campagnelab.dl.genotype.mappers;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.dl.framework.mappers.ConfigurableFeatureMapper;
import org.campagnelab.dl.framework.mappers.FeatureNameMapper;
import org.campagnelab.dl.somatic.mappers.*;
import org.campagnelab.dl.somatic.mappers.functional.TraversalHelper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Properties;
import java.util.Set;


/**
 * A somatic feature mapper that reuses GenotypeMapperV28 and adds somatic specific features.
 */
public class SomaticFeatureMapper1 extends NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>
        implements ConfigurableFeatureMapper {
    private FeatureNameMapper<BaseInformationRecords.BaseInformationOrBuilder> delegate;

    private String recordTo(final int contextLength, BaseInformationRecords.BaseInformationOrBuilder record, int countIndex) {
        return MappingFunctions.recordTo(contextLength, record, countIndex);
    }

    int MAX_GENOTYPES;
    boolean withCombinedLayer;
    boolean withDistinctAlleleCounts;
    int germlineIndex;
    int somaticIndex;


    public SomaticFeatureMapper1() {
        this(0,1);
        System.out.println("Somatic Feature Mapper instantiated with defaults:\n" +
                "germline = sample 0 in sbi/protobuf, somatic = sample 1 in sbi/protobuf");
    }

    public SomaticFeatureMapper1(int germlineIndex, int somaticIndex) {
        super();
        this.germlineIndex=germlineIndex;
        this.somaticIndex=somaticIndex;
        withDistinctAlleleCounts = true;
        withCombinedLayer = false;
        MAX_GENOTYPES = 3;
    }



    /**
     * Configure the feature mapper for a specific set of sbi files. This method accesses the properties of the reader.
     *
     * @param sbiProperties properties from an sbi reader.
     */
    public void configure(Properties sbiProperties) {


        Set<Integer> sampleIndices = new ObjectArraySet<>();
        sampleIndices.add(germlineIndex);
        sampleIndices.add(somaticIndex);

        FeatureNameMapper[] matchesRefMappers = new FeatureNameMapper[MAX_GENOTYPES];
        FeatureNameMapper[] firstBaseMappers = new FeatureNameMapper[MAX_GENOTYPES];
        FeatureNameMapper[] originalGobyCountIndexMappers = new FeatureNameMapper[MAX_GENOTYPES];



        int genotypeIndex = 0;

        for (int i = 0; i < MAX_GENOTYPES; i++) {
            final int constantGenotypeIndex = genotypeIndex;

            matchesRefMappers[i] = (new MatchesReferenceMapper(somaticIndex, i));
            firstBaseMappers[i] = new GenomicContextMapper(1,
                    record -> record.getSamples(somaticIndex).getCounts(constantGenotypeIndex).getToSequence().substring(0, 1));
            originalGobyCountIndexMappers[i] = new OriginalGobyCountIndexMapper(somaticIndex, constantGenotypeIndex);
        }

        final OneSampleMapperUnsortedV1 a = new OneSampleMapperUnsortedV1(germlineIndex);
        final OneSampleMapperUnsortedV1 b = new OneSampleMapperUnsortedV1(somaticIndex);
        a.configure(sbiProperties);
        b.configure(sbiProperties);
        delegate = new CountReorderingMapper(somaticIndex, new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(
                a,
                b,
                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(matchesRefMappers),
                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(originalGobyCountIndexMappers),
                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(firstBaseMappers),
                new DensityMapper("numVariationsInRead", 20, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forNSampleCounts(sampleIndices, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getNumVariationsInReadsList)),
                new DensityMapper("readMappingQuality.forward", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forNSampleCounts(sampleIndices, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getReadMappingQualityForwardStrandList)),
                new DensityMapper("readMappingQuality.reverse", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forNSampleCounts(sampleIndices, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getReadMappingQualityReverseStrandList)),
                new DensityMapper("baseQuality.forward", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forNSampleCounts(sampleIndices, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getQualityScoresForwardStrandList)),
                new DensityMapper("baseQuality.reverse", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forNSampleCounts(sampleIndices, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getQualityScoresReverseStrandList)),
                new DensityMapper("insertSizes", 10, sbiProperties, (BaseInformationRecords.BaseInformationOrBuilder baseInformationOrBuilder) -> {
                    return TraversalHelper.forNSampleCounts(sampleIndices, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getInsertSizesList);
                },
                        insertSize -> (float)Math.log10(insertSize)),
                new FractionDifferences4(),
                new MagnitudeFeatures2()
        ));

        numFeatures = delegate.numberOfFeatures();

    }


    @Override
    public String getFeatureName(int i) {
        return delegate.getFeatureName(i);
    }

    @Override
    public int numberOfFeatures() {
        return delegate.numberOfFeatures();
    }

    @Override
    public void prepareToNormalize(BaseInformationRecords.BaseInformationOrBuilder record, int indexOfRecord) {
        delegate.prepareToNormalize(record, indexOfRecord);
    }

    @Override
    public void mapFeatures(BaseInformationRecords.BaseInformationOrBuilder record, INDArray inputs, int indexOfRecord) {
        delegate.mapFeatures(record, inputs, indexOfRecord);
    }

    @Override
    public float produceFeature(BaseInformationRecords.BaseInformationOrBuilder record, int featureIndex) {
        return delegate.produceFeature(record, featureIndex);
    }

}