/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bacon.pig.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.jboss.pnc.bacon.pig.impl.config.PigConfiguration;
import org.jboss.pnc.bacon.pig.impl.documents.Deliverables;
import org.jboss.pnc.bacon.pig.impl.pnc.ImportResult;
import org.jboss.pnc.bacon.pig.impl.pnc.PncBuild;
import org.jboss.pnc.bacon.pig.impl.repo.RepositoryData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.lang.System.getProperty;

/**
 * TODO: consider saving the latest reached state to not repeat the steps already performed
 *
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com <br>
 *         Date: 4/1/19
 */
@Data
public class PigContext {
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String contextLocation = getProperty(
            "pig.context.dir",
            getProperty("java.io.tmpdir") + File.separator + "pig-context");

    private PigConfiguration pigConfiguration; // TODO merge config instead of setting it?
    private ImportResult pncImportResult;
    private Map<String, PncBuild> builds;
    private RepositoryData repositoryData;
    private Deliverables deliverables;

    private String targetPath;
    private String releasePath;
    private String extrasPath;

    private boolean tempBuild;

    public void setPigConfiguration(PigConfiguration pigConfiguration) {
        this.pigConfiguration = pigConfiguration;
        if (deliverables == null) {
            String prefix = String.format(
                    "%s-%s.%s",
                    pigConfiguration.getOutputPrefixes().getReleaseFile(),
                    pigConfiguration.getVersion(),
                    pigConfiguration.getMilestone());

            deliverables = new Deliverables();

            deliverables.setRepositoryZipName(prefix + "-maven-repository.zip");
            deliverables.setLicenseZipName(prefix + "-license.zip");
            deliverables.setSourceZipName(prefix + "-src.zip");
            deliverables.setJavadocZipName(prefix + "-javadoc.zip");
            deliverables.setNvrListName(prefix + "-nvr-list.txt");
        }
        configureTargetDirectories(pigConfiguration);
    }

    private void configureTargetDirectories(PigConfiguration pigConfiguration) {
        String productPrefix = pigConfiguration.getProduct().prefix();
        targetPath = "target"; // TODO: a way to customize it
        releasePath = targetPath + "/" + productPrefix + "-" + pigConfiguration.getVersion() + "."
                + pigConfiguration.getMilestone() + "/";

        File releaseDirectory = Paths.get(releasePath).toFile();
        if (!releaseDirectory.isDirectory()) {
            releaseDirectory.mkdirs();
        }
        releasePath = releaseDirectory.getAbsolutePath();
        releasePath = releasePath.endsWith("/") ? releasePath : releasePath + '/';

        File extrasDirectory = Paths.get(releasePath, "extras").toFile();
        if (!extrasDirectory.isDirectory()) {
            extrasDirectory.mkdirs();
        }
        extrasPath = extrasDirectory.getAbsolutePath();
        extrasPath = extrasPath.endsWith("/") ? extrasPath : extrasPath + '/';
    }

    public void storeContext() {
        try (OutputStream output = new FileOutputStream(contextLocation)) {
            jsonMapper.writerFor(PigContext.class).writeValue(output, this);
        } catch (IOException e) {
            throw new RuntimeException("failed to store PigContext", e);
        }
    }

    public void loadConfig(String configDirStr) {
        Path configDir = Paths.get(configDirStr);
        File configFile = configDir.resolve("build-config.yaml").toFile();
        if (configFile.exists()) {
            try (FileInputStream configStream = new FileInputStream(configFile)) {
                setPigConfiguration(PigConfiguration.load(configStream));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read config file: " + configFile.getAbsolutePath(), e);
            }
        } else {
            throw new MisconfigurationException(
                    "The provided config file: " + configFile.getAbsolutePath() + " does not exist");
        }
    }

    /*
     * STATICS
     */
    private static PigContext instance;

    static {
        instance = readContext();
    }

    public static PigContext get() {
        return instance;
    }

    private static PigContext readContext() {
        if (getProperty("pig.continue") != null) {
            File contextFile = new File(contextLocation);
            if (contextFile.exists()) {
                try (InputStream input = new FileInputStream(contextFile)) {
                    return jsonMapper.readerFor(PigContext.class).readValue(input);
                } catch (IOException e) {
                    throw new RuntimeException("failed to read PigContext", e);
                }
            }
        }
        return new PigContext();
    }
}
