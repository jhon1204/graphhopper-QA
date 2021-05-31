/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.reader.dem.SkadiProvider;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OSMMaxSpeedParser;
import com.graphhopper.routing.util.parsers.OSMRoadEnvironmentParser;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.*;

import static com.graphhopper.Junit4To5Assertions.*;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Curbsides.*;
import static com.graphhopper.util.Parameters.Routing.U_TURN_COSTS;
import static java.util.Arrays.asList;

/**
 * @author Peter Karich
 */
public class GraphHopperQairaTest {

    public static final String DIR = "../core/files";

    // map locations
    private static final String MONACO = DIR + "/monaco.osm.gz";
    private static final String DESIRED = DIR + "/area_destino.osm";

    // when creating GH instances make sure to use this as the GH location such that it will be cleaned between tests
    private static final String GH_LOCATION = "target/graphhopper-test-gh";

    @Test
    public void testMonacoDifferentAlgorithms() {
        String algo="aco";
        boolean withCH=false;
        int expectedVisitedNodes=501;
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = createGraphHopper(vehicle).
                setOSMFile(DESIRED).
                setProfiles(new Profile("profile").setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler()
                .setCHProfiles(new CHProfile("profile"))
                .setDisablingAllowed(true);
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();
        GHRequest req = new GHRequest(-12.04975,-77.049673,-12.041461,-77.039008)
                .setAlgorithm(algo)
                .setProfile("profile");
        req.putHint(CH.DISABLE, !withCH);
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
//        assertEquals(expectedVisitedNodes, rsp.getHints().getLong("visited_nodes.sum", 0));

        ResponsePath res = rsp.getBest();
        System.out.println(res.getPoints());
//        assertEquals(3587.9, res.getDistance(), .1);
//        assertEquals(277173, res.getTime(), 10);
//        assertEquals(91, res.getPoints().getSize());
//          
//        assertEquals(43.7276852, res.getWaypoints().getLat(0), 1e-7);
//        assertEquals(43.7495432, res.getWaypoints().getLat(1), 1e-7);
    }

    

    private void testImportCloseAndLoad(boolean ch, boolean lm, boolean sort) {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = createGraphHopper(vehicle).
                setOSMFile(MONACO).
                setProfiles(Collections.singletonList(new Profile(profile).setVehicle(vehicle).setWeighting(weighting))).
                setStoreOnFlush(true).
                setSortGraph(sort);
        if (ch) {
            hopper.getCHPreparationHandler()
                    .setCHProfiles(new CHProfile(profile))
                    .setDisablingAllowed(true);
        }
        if (lm) {
            hopper.getLMPreparationHandler()
                    .setLMProfiles(new LMProfile(profile))
                    .setDisablingAllowed(true);
        }
        hopper.importAndClose();
        hopper = createGraphHopper(vehicle).
                setOSMFile(MONACO).
                setProfiles(Collections.singletonList(new Profile(profile).setVehicle(vehicle).setWeighting(weighting))).
                setStoreOnFlush(true);
        if (ch) {
            hopper.getCHPreparationHandler()
                    .setCHProfiles(new CHProfile(profile))
                    .setDisablingAllowed(true);
        }
        if (lm) {
            hopper.getLMPreparationHandler()
                    .setLMProfiles(new LMProfile(profile))
                    .setDisablingAllowed(true);
        }
        hopper.importOrLoad();

        // same query as in testMonacoWithInstructions
        // visited nodes >700 for flexible, <120 for CH or LM

        if (ch) {
            GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setProfile(profile);
            req.putHint(CH.DISABLE, false);
            req.putHint(Landmark.DISABLE, true);
            GHResponse rsp = hopper.route(req);

            ResponsePath bestPath = rsp.getBest();
            long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
            assertNotEquals(sum, 0);
            assertTrue("Too many nodes visited " + sum, sum < 120);
            assertEquals(3437.6, bestPath.getDistance(), .1);
            assertEquals(85, bestPath.getPoints().getSize());
        }

        if (lm) {
            GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setProfile(profile).
                    setAlgorithm(Parameters.Algorithms.ASTAR_BI);
            req.putHint(CH.DISABLE, true);
            req.putHint(Landmark.DISABLE, false);
            GHResponse rsp = hopper.route(req);

            ResponsePath bestPath = rsp.getBest();
            long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
            assertNotEquals(sum, 0);
            assertTrue("Too many nodes visited " + sum, sum < 120);
            assertEquals(3437.6, bestPath.getDistance(), .1);
            assertEquals(85, bestPath.getPoints().getSize());
        }

        // flexible
        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profile);
        req.putHint(CH.DISABLE, true);
        req.putHint(Landmark.DISABLE, true);
        GHResponse rsp = hopper.route(req);

        ResponsePath bestPath = rsp.getBest();
        long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertNotEquals(sum, 0);
        assertTrue("Too few nodes visited " + sum, sum > 120);
        assertEquals(3437.6, bestPath.getDistance(), .1);
        assertEquals(85, bestPath.getPoints().getSize());

        hopper.close();
    }

    private void assertInstruction(Instruction instruction, String expectedName, String expectedInterval, int expectedLength, int expectedPoints) {
        assertEquals(expectedName, instruction.getName());
        assertEquals(expectedInterval, ((ShallowImmutablePointList) instruction.getPoints()).getIntervalString());
        assertEquals(expectedLength, instruction.getLength());
        assertEquals(expectedPoints, instruction.getPoints().size());
    }

    private void assertDetail(PathDetail detail, String expected) {
        assertEquals(expected, detail.toString());
    }

    private static GraphHopperOSM createGraphHopper(String encodingManagerString) {
        GraphHopperOSM hopper = new GraphHopperOSM();
        hopper.setEncodingManager(EncodingManager.create(encodingManagerString));
        hopper.setGraphHopperLocation(GH_LOCATION);
        return hopper;
    }

}
