/* Copyright 2017 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.config;

import com.google.api.codegen.MethodConfigProto;
import com.google.api.codegen.ReleaseLevel;
import com.google.api.codegen.ResourceNameTreatment;
import com.google.api.codegen.SurfaceTreatmentProto;
import com.google.api.codegen.VisibilityProto;
import com.google.api.codegen.config.GrpcStreamingConfig.GrpcStreamingType;
import com.google.api.codegen.discovery.Method;
import com.google.api.codegen.transformer.SurfaceNamer;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.joda.time.Duration;

/**
 * GapicMethodConfig represents the code-gen config for a Discovery doc method, and includes the
 * specification of features like page streaming and parameter flattening.
 */
@AutoValue
public abstract class DiscoGapicMethodConfig extends MethodConfig {
  public abstract Method getMethod();

  @Override
  public boolean isGrpcStreaming() {
    return false;
  }

  @Override
  public String getRerouteToGrpcInterface() {
    return null;
  }

  @Override
  /* Returns the grpc streaming configuration of the method. */
  public GrpcStreamingType getGrpcStreamingType() {
    return GrpcStreamingType.NonStreaming;
  }

  @Nullable
  @Override
  public GrpcStreamingConfig getGrpcStreaming() {
    return null;
  }

  /**
   * Creates an instance of DiscoGapicMethodConfig based on MethodConfigProto, linking it up with
   * the provided method. On errors, null will be returned, and diagnostics are reported to the diag
   * collector.
   */
  @Nullable
  static DiscoGapicMethodConfig createDiscoGapicMethodConfig(
      DiagCollector diagCollector,
      String language,
      MethodConfigProto methodConfigProto,
      com.google.api.codegen.discovery.Method method,
      ImmutableSet<String> retryCodesConfigNames,
      ImmutableSet<String> retryParamsConfigNames) {

    boolean error = false;

    //TODO(andrealin): Paging, batching, and retry configs.

    String retryCodesName = methodConfigProto.getRetryCodesName();
    if (!retryCodesName.isEmpty() && !retryCodesConfigNames.contains(retryCodesName)) {
      diagCollector.addDiag(
          Diag.error(
              SimpleLocation.TOPLEVEL,
              "Retry codes config used but not defined: '%s' (in method %s)",
              retryCodesName,
              method.id()));
      error = true;
    }

    String retryParamsName = methodConfigProto.getRetryParamsName();
    if (!retryParamsConfigNames.isEmpty() && !retryParamsConfigNames.contains(retryParamsName)) {
      diagCollector.addDiag(
          Diag.error(
              SimpleLocation.TOPLEVEL,
              "Retry parameters config used but not defined: %s (in method %s)",
              retryParamsName,
              method.id()));
      error = true;
    }

    Duration timeout = Duration.millis(methodConfigProto.getTimeoutMillis());
    if (timeout.getMillis() <= 0) {
      diagCollector.addDiag(
          Diag.error(
              SimpleLocation.TOPLEVEL,
              "Default timeout not found or has invalid value (in method %s)",
              method.id()));
      error = true;
    }

    boolean hasRequestObjectMethod = methodConfigProto.getRequestObjectMethod();

    ImmutableMap<String, String> fieldNamePatterns =
        ImmutableMap.copyOf(methodConfigProto.getFieldNamePatterns());

    ResourceNameTreatment defaultResourceNameTreatment =
        methodConfigProto.getResourceNameTreatment();
    if (defaultResourceNameTreatment == null
        || defaultResourceNameTreatment.equals(ResourceNameTreatment.UNSET_TREATMENT)) {
      defaultResourceNameTreatment = ResourceNameTreatment.NONE;
    }

    Iterable<FieldConfig> requiredFieldConfigs =
        DiscoGapicMethodConfig.createFieldNameConfigs(
            DiscoGapicMethodConfig.getRequiredFields(
                diagCollector, method, methodConfigProto.getRequiredFieldsList()));

    Iterable<FieldConfig> optionalFieldConfigs =
        DiscoGapicMethodConfig.createFieldNameConfigs(
            DiscoGapicMethodConfig.getOptionalFields(
                method, methodConfigProto.getRequiredFieldsList()));

    List<String> sampleCodeInitFields = new ArrayList<>();
    sampleCodeInitFields.addAll(methodConfigProto.getRequiredFieldsList());
    sampleCodeInitFields.addAll(methodConfigProto.getSampleCodeInitFieldsList());

    VisibilityConfig visibility = VisibilityConfig.PUBLIC;
    ReleaseLevel releaseLevel = ReleaseLevel.ALPHA;
    for (SurfaceTreatmentProto treatment : methodConfigProto.getSurfaceTreatmentsList()) {
      if (!treatment.getIncludeLanguagesList().contains(language)) {
        continue;
      }
      if (treatment.getVisibility() != VisibilityProto.UNSET_VISIBILITY) {
        visibility = VisibilityConfig.fromProto(treatment.getVisibility());
      }
      if (treatment.getReleaseLevel() != ReleaseLevel.UNSET_RELEASE_LEVEL) {
        releaseLevel = treatment.getReleaseLevel();
      }
    }

    LongRunningConfig longRunningConfig = null;

    if (error) {
      return null;
    } else {
      return new AutoValue_DiscoGapicMethodConfig(
          null,
          null,
          retryCodesName,
          retryParamsName,
          timeout,
          requiredFieldConfigs,
          optionalFieldConfigs,
          defaultResourceNameTreatment,
          null,
          hasRequestObjectMethod,
          fieldNamePatterns,
          sampleCodeInitFields,
          visibility,
          releaseLevel,
          longRunningConfig,
          method);
    }
  }

  @Override
  /* Return the list of "one of" instances associated with the fields. */
  public Iterable<Iterable<String>> getOneofNames(SurfaceNamer namer) {
    return ImmutableList.of();
  }
}