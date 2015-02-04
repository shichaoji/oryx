/*
 * Copyright (c) 2015, Cloudera and Intel, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */

package com.cloudera.oryx.app.kmeans;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import org.dmg.pmml.Cluster;
import org.dmg.pmml.ClusteringField;
import org.dmg.pmml.ClusteringModel;
import org.dmg.pmml.ComparisonMeasure;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldUsageType;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMML;
import org.dmg.pmml.SquaredEuclidean;

import com.cloudera.oryx.app.pmml.AppPMMLUtils;
import com.cloudera.oryx.app.schema.InputSchema;
import com.cloudera.oryx.common.math.VectorMath;
import com.cloudera.oryx.common.pmml.PMMLUtils;
import com.cloudera.oryx.common.text.TextUtils;

public final class KMeansPMMLUtils {

  private KMeansPMMLUtils() {}

  /**
   * Validates that the encoded PMML model received matches expected schema.
   *
   * @param pmml {@link org.dmg.pmml.PMML} encoding of KMeans Clustering
   * @param schema expected schema attributes of KMeans Clustering
   */
  public static void validatePMMLVsSchema(PMML pmml, InputSchema schema) {
    List<Model> models = pmml.getModels();
    Preconditions.checkArgument(models.size() == 1,
        "Should have exactly one model, but had %s", models.size());

    Model model = models.get(0);
    Preconditions.checkArgument(model instanceof ClusteringModel);
    Preconditions.checkArgument(model.getFunctionName() == MiningFunctionType.CLUSTERING);

    DataDictionary dictionary = pmml.getDataDictionary();
    Preconditions.checkArgument(
        schema.getFeatureNames().equals(AppPMMLUtils.getFeatureNames(dictionary)),
        "Feature names in schema don't match names in PMML");

    MiningSchema miningSchema = model.getMiningSchema();
    Preconditions.checkArgument(schema.getFeatureNames().equals(
        AppPMMLUtils.getFeatureNames(miningSchema)));

  }

  /**
   * @param pmml PMML representation of Clusters
   * @return List of {@link ClusterInfo}
   */
  public static List<ClusterInfo> read(PMML pmml) {
    List<Model> models = pmml.getModels();
    Model model = models.get(0);

    Preconditions.checkArgument(model instanceof ClusteringModel);
    ClusteringModel clusteringModel = (ClusteringModel) model;

    List<Cluster> clusters = clusteringModel.getClusters();
    List<ClusterInfo> clusterInfoList = new ArrayList<>(clusters.size());

    for (Cluster cluster : clusters) {
      String[] tokens = TextUtils.parseDelimited(cluster.getArray().getValue(), ' ');
      ClusterInfo clusterInfo = new ClusterInfo(Integer.parseInt(cluster.getId()),
                                                VectorMath.parseVector(tokens),
                                                cluster.getSize());
      clusterInfoList.add(clusterInfo);
    }

    return clusterInfoList;
  }

  public static PMML buildDummyClusteringModel() {
    PMML pmml = PMMLUtils.buildSkeletonPMML();

    List<DataField> dataFields = new ArrayList<>();
    dataFields.add(new DataField(FieldName.create("x"), OpType.CONTINUOUS, DataType.DOUBLE));
    dataFields.add(new DataField(FieldName.create("y"), OpType.CONTINUOUS, DataType.DOUBLE));
    DataDictionary dataDictionary = new DataDictionary(dataFields);
    dataDictionary.setNumberOfFields(dataFields.size());
    pmml.setDataDictionary(dataDictionary);

    List<MiningField> miningFields = new ArrayList<>();
    MiningField xMF = new MiningField(FieldName.create("x"));
    xMF.setOpType(OpType.CONTINUOUS);
    xMF.setUsageType(FieldUsageType.ACTIVE);
    miningFields.add(xMF);
    MiningField yMF = new MiningField(FieldName.create("y"));
    yMF.setOpType(OpType.CONTINUOUS);
    yMF.setUsageType(FieldUsageType.ACTIVE);
    miningFields.add(yMF);
    MiningSchema miningSchema = new MiningSchema(miningFields);

    List<ClusteringField> clusteringFields = new ArrayList<>();
    clusteringFields.add(new ClusteringField(
        FieldName.create("x")).withCenterField(ClusteringField.CenterField.TRUE));
    clusteringFields.add(new ClusteringField(
        FieldName.create("y")).withCenterField(ClusteringField.CenterField.TRUE));

    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster().withId("0").withSize(1).withArray(AppPMMLUtils.toArray(1.0, 0.0)));
    clusters.add(new Cluster().withId("1").withSize(2).withArray(AppPMMLUtils.toArray(2.0, -1.0)));
    clusters.add(new Cluster().withId("2").withSize(3).withArray(AppPMMLUtils.toArray(-1.0, 0.0)));

    pmml.getModels().add(new ClusteringModel(
        MiningFunctionType.CLUSTERING,
        ClusteringModel.ModelClass.CENTER_BASED,
        clusters.size(),
        miningSchema,
        new ComparisonMeasure(ComparisonMeasure.Kind.DISTANCE).withMeasure(new SquaredEuclidean()),
        clusteringFields,
        clusters));

    return pmml;
  }

}
