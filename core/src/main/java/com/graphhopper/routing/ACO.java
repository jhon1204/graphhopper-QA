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
package com.graphhopper.routing;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.carrotsearch.hppc.cursors.IntShortCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 *  Implements Ant Colony Optimization Algorithm
 *
 * @author Jhon
 */
public class ACO extends AbstractRoutingAlgorithm {
    protected HashMap<String,ACOEntry> fromMap;
    protected PriorityQueue<ACOEntry> fromHeap;
    protected ACOEntry currEdge;
    private ACOEntry bestPath=null;
    private int visitedNodes;
    private int to = -1;
    private int ANTS=100;
    private int MAX_ITER=100;
    private double RHO=0.5;
    private double PHEROMONE=5;
    private int ELITEANTS=50;
    private double ALPHA=0.3;
    private double BETA=1.2;

    

    public ACO(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
    }

    protected void initCollections(int size) {
        fromHeap = new PriorityQueue<>(size);
        fromMap = new HashMap<>(size);
    }

    @Override
    public Path calcPath(int from, int to) {
        checkAlreadyRun();
        this.to = to;
        bestPath=null;
        
        for (int i = 0; i < this.MAX_ITER; i++) {
            StopWatch sw = new StopWatch().start();
            for (int j = 0; j < this.ANTS; j++) {// for each of the ants
                currEdge = new ACOEntry(EdgeIterator.NO_EDGE, from, 0,0 , 0);
                if (!traversalMode.isEdgeBased() && i==0 && j==0) {
                    fromMap.put(String.valueOf(from), currEdge);
                    
                }
                runAlgo(from);
            }
            // update pheromones
            this.depositPheromone();
            if(bestPath==null){
                bestPath=fromHeap.poll();
            }else {
                ACOEntry aux=fromHeap.poll();
                if(bestPath.weight>aux.weight){
                    bestPath=aux;
                }
            }
            this.evaporatePheromones();
            String important="Iter: "+i+", duration of iter: "+sw.stop().getSeconds()+"s,";
            this.currEdge=bestPath;
            Path tmpPath= extractPath();
            important+="visitedNodes: "+ tmpPath.getEdgeCount()+", distance: "+tmpPath.getDistance()+",time: "+tmpPath.getTime()+"ms";
            File output= new File("outputIterACO.txt");
            FileWriter writer;
            try {
                writer = new FileWriter(output, true);
                writer.write(important+"\n");
                writer.flush();
                writer.close();
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
        this.currEdge=bestPath;
        return extractPath();
    }
    public void evaporatePheromones(){
        Set<String> array=fromMap.keySet();
        for (String i : array) {
            ACOEntry edge=fromMap.get(i);
            edge.pheromone=edge.pheromone*(1-this.RHO);
            fromMap.put(i, edge);
        }
    }
    public void depositPheromone(){
        PriorityQueue<ACOEntry> aux=new PriorityQueue<>(fromHeap);
        for (int i = 0; i < this.ELITEANTS; i++) {
            ACOEntry path=aux.poll();
            ACOEntry currEntry = path;
            while (currEntry!=null && EdgeIterator.Edge.isValid(currEntry.edge)) {
                int key=currEntry.adjNode;
                int key0=currEntry.getParent().adjNode;
                ACOEntry auxEdge=fromMap.get(String.valueOf(key0)+"-"+String.valueOf(key));
                auxEdge.addPheromone(this.PHEROMONE/auxEdge.getWeightNode());
                fromMap.put(String.valueOf(key0)+"-"+String.valueOf(key), auxEdge);
                currEntry = auxEdge.getParent();
            }
        }
    }

    protected void runAlgo(int from) {
        this.visitedNodes=0;
        int prevnode=-1;
        ArrayList<Integer> prevNodes=new ArrayList<>();
        prevNodes.add(prevnode);
        ArrayList<Integer> prevNodes1=new ArrayList<>();
        prevNodes1.add(from);
        ArrayList<String> Nodes= new ArrayList<>();
        Nodes.add(String.valueOf(from));
        while (true) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            LinkedHashMap<String,ACOEntry> candidates=new LinkedHashMap<>();
            while (iter.next()) {
                if(Nodes.indexOf(String.valueOf(currNode)+"-"+String.valueOf(iter.getAdjNode()))!=-1)
                    continue;
                if (!accept(iter, currEdge.edge))
                    continue;
                if(iter.getAdjNode()==from)
                    continue;
                if(iter.getAdjNode()==prevnode)
                    continue;
                if(prevNodes.indexOf(iter.getAdjNode())!=-1)
                    continue;
                // todo: for #1776/#1835 move the access check into weighting
                double tmpWeight = !outEdgeFilter.accept(iter)
                        ? Double.POSITIVE_INFINITY
                        : (GHUtility.calcWeightWithTurnWeight(weighting, iter, false, currEdge.edge));
                if (Double.isInfinite(tmpWeight)) {
                    continue;
                }

                int traversalId = traversalMode.createTraversalId(iter, false);
                ACOEntry nEdge = fromMap.get(String.valueOf(currNode)+"-"+String.valueOf(traversalId));
                if(nEdge==null){
                    nEdge= new ACOEntry(iter.getEdge(), iter.getAdjNode(),tmpWeight+ currEdge.weight,tmpWeight,0.0);
                    nEdge.parent=currEdge;
                    candidates.put(String.valueOf(currNode)+"-"+String.valueOf(iter.getAdjNode()), nEdge);
                }else{
                    nEdge.parent=currEdge;
                    candidates.put(String.valueOf(currNode)+"-"+String.valueOf(iter.getAdjNode()), nEdge);
                }
            
                    Nodes.add(String.valueOf(currNode)+"-"+String.valueOf(iter.getAdjNode()));
            }   
            // after getting all of the neighbors
            if(candidates.size()!=0){
            String selected= this.getNextNode(candidates,prevNodes1);
            ACOEntry selectedE=fromMap.get(selected);
            if(selectedE==null){
                selectedE=candidates.get(selected);
                selectedE.parent=currEdge;
                fromMap.put(selected, selectedE);
            }
            prevNodes1.add(selectedE.adjNode);
            this.currEdge=selectedE;
            ACOEntry parent=selectedE.getParent();
            if(parent!=null)
                prevnode=parent.getParent()==null?-1: parent.getParent().adjNode;
            else
                prevnode=-1;
            if(prevnode!=-1)
                prevNodes.add(prevnode);
            }else{
                // if not edge that connects
                break;
            }
            
        }
        // save the path of the ant
        if(finished()){
            ACOEntry pathAnt=currEdge.clone();
            pathAnt.parent=currEdge.getParent();
            fromHeap.add(pathAnt);
        }
        
    }
    public String getNextNode(LinkedHashMap<String,ACOEntry> candidates, ArrayList<Integer> prevNodes1){
        double total=0;
        for (Map.Entry<String, ACOEntry> entry : candidates.entrySet()) {
            String key = entry.getKey();
            ACOEntry val = entry.getValue();
            if(prevNodes1.indexOf(val.adjNode)==-1){
                total+= Math.pow(1/val.getWeightOfVisitedPath(),this.ALPHA)*Math.pow(val.getPheromone(), this.BETA);   
            }    
            else{
                val.pheromone=0;
                total+= Math.pow(1/val.getWeightOfVisitedPath(),this.ALPHA)*Math.pow(val.getPheromone(), this.BETA);      
            }
        }
        Random rand= new Random();
        if(total!=0){
            double randomI= rand.nextDouble()*total;
            double newTotal=0;
            for (Map.Entry<String, ACOEntry> entry : candidates.entrySet()) {
                String key = entry.getKey();
                ACOEntry val = entry.getValue();
                newTotal+=Math.pow(1/val.getWeightOfVisitedPath(),this.ALPHA)*Math.pow(val.getPheromone(), this.BETA);
                if(newTotal>=randomI)
                    return key;
            }
        }
        // if not found random with probabilities, just return a random one
        List<String> aux=new ArrayList<String>(candidates.keySet());
        return aux.get(rand.nextInt(aux.size()));
    }

    @Override
    protected boolean finished() {
        return currEdge.adjNode == to;
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();
        return PathExtractor.extractPath(graph, weighting, currEdge);
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry bestSPTEntry, int traversalId) {
    }



    public static class ACOEntry extends SPTEntry{
        double pheromone;
        double weightNode;

        public ACOEntry(int edgeId, int adjNode, double weightForHeap,double weightofNode, double pheromone) {
            super(edgeId,adjNode,weightForHeap);
            this.pheromone = pheromone;
            this.weightNode=weightofNode;
        }
        public void addPheromone(double pher){
            this.pheromone+=pher;
        }
        public double getPheromone(){
                return this.pheromone;
        }
        public double getWeightNode(){
            return this.weightNode;
        }

        @Override
        public ACOEntry getParent() {
            return (ACOEntry) parent;
        }

        @Override
        public ACOEntry clone() {
            return new ACOEntry(edge, adjNode, weight,weightNode,pheromone);
        }

    }
    
    
    
    
    @Override
    public String getName() {
        return "ACO";
    }
}
