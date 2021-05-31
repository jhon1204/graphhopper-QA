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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.QairaWeightingCO;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.createMockedEdgeIteratorState;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;

/**
 * @author Jhon
 */
public class QairaWeightingCOTest {
    
    private EncodingManager encodingManager;
    private  FlagEncoder encoder;
    private GraphHopperStorage graph;

    @Before
    public void setUp() {
        encodingManager= EncodingManager.create("foot");
        encoder= encodingManager.getEncoder("foot");
        graph = new GraphBuilder(encodingManager).create();
        // 0-1
        graph.edge(0, 1, 1, true);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
    }
    @Test
    public void testCOWeight() {
        EdgeIteratorState edge = createMockedEdgeIteratorState(10, GHUtility.setProperties(encodingManager.createEdgeFlags(), encoder, 15, true, false));
        Weighting instance = new QairaWeightingCO(encoder,TurnCostProvider.NO_TURN_COST_PROVIDER);
        instance.setGraph(graph.getBaseGraph());
        // should be 0.00 because it is not found (0.00,0.00) (0.99,0.99) coordinates are not in DB 
        assertEquals(0.00, instance.calcEdgeWeight(edge, true), 1e-8);

    }
    @Test
    public void testConstructor0(){
        try {
            Weighting instance= new QairaWeightingCO(encoder);
            instance.setGraph(graph.getBaseGraph());
        } catch (Exception e) {
        }
        
    }

    
    @Test
    public void testgetDistanceFromLatLonInM(){
        QairaWeightingCO instance = new QairaWeightingCO(encoder);
        instance.setGraph(graph.getBaseGraph());
        assertEquals(111, instance.getDistanceFromLatLonInM(0, 0, 0.001, 0.0),3e-1);
    }
}
