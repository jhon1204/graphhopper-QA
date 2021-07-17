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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.ini4j.Ini;
import java.io.FileReader;
import java.util.HashMap;
import java.lang.String;
import java.lang.Double;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import com.graphhopper.storage.Graph;
import java.io.File;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;



public class QairaWeightingNO2 extends AbstractWeighting {
    private Graph graph;
    private Connection con;
    private HashMap<String,Double> cellsDist;


    public QairaWeightingNO2(FlagEncoder flagEncoder) {
        this(flagEncoder, NO_TURN_COST_PROVIDER);
            
    }
    public String getPollutant(){
        return "NO2";
    }
    @Override
    public void setGraph(Graph graph){
        this.graph=graph;
        
    }
    public QairaWeightingNO2(FlagEncoder flagEncoder, TurnCostProvider turnCostProvider){
        super(flagEncoder,NO_TURN_COST_PROVIDER);
        this.cellsDist= new HashMap<String,Double>();
        String realPath=System.getProperty("user.dir");
        //String fileName = "./sensors-config/" + MYSQL ".config";
        String fileName = realPath+"/sensors-config/MYSQL.config";
        String host="";
        String user="";
        String password="";
        String schema ="";
        String database="";
        System.out.println("filename= "+fileName);
        try {
            Ini ini = new Ini(new FileReader(fileName));
            host = ini.get("ConnectionSettings").fetch("MYSQL_URL");
            user = ini.get("ConnectionSettings").fetch("USER");
            password = ini.get("ConnectionSettings").fetch("PASSWORD");
            schema = ini.get("ConnectionSettings").fetch("SCHEMA");   
            database = ini.get("ConnectionSettings").fetch("DATABASE"); 
            
        } catch (Exception e) {
            System.out.println("com.graphhopper.routing.weighting.QairaWeightingCO.<init>()");
        }
        String url="jdbc:postgresql://"+host+":5432/"+database+"?currentSchema="+schema;
        try {
            Class.forName("org.postgresql.Driver").newInstance();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        try {
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            
            this.con= DriverManager.getConnection(url,props);
            
        } catch (SQLException ex) {
            //TODO: handle exception
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }


    }

    @Override
    public double getMinWeight(double currDistToGoal) {
        return currDistToGoal;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        if(this.graph!=null){
        double latIni=this.graph.getNodeAccess().getLatitude(edgeState.getBaseNode());
        double lonIni=this.graph.getNodeAccess().getLongitude(edgeState.getBaseNode());
        double latEnd=this.graph.getNodeAccess().getLatitude(edgeState.getAdjNode());
        double lonEnd=this.graph.getNodeAccess().getLongitude(edgeState.getAdjNode());
        HashMap<String,Double> hm=this.getDistances(latIni, lonIni, latEnd, lonEnd);
        List<String> lID=new ArrayList<String>(hm.keySet());
        String cellID="";
        String query= "select i.\"idinterpolation_algorithm\",i.\"idcell\",p.\"pollutantName\",i.\"interpolatedValiue\" from interpolatedmetrics i, pollutant p where p.\"idPollutant\"=i.\"idPollutant\" and i.idcell=? and p.\"pollutantName\"=? order by idcell;";
        HashMap<String,Double[]> values= new HashMap<>();
        for (int i = 0; i < lID.size(); i++) {
            cellID=lID.get(i);
            try {
                PreparedStatement ps=this.con.prepareStatement(query);
                ps.setString(1, cellID);
                ps.setString(2, "NO2");
                ResultSet rs=ps.executeQuery();
                if(!rs.next())
                    break;
                double val=rs.getDouble("interpolatedValiue");
                Double vals[]=new Double[2];
                vals[0]=hm.get(cellID); // distance
                vals[1]=val;// metric
                values.put(cellID, vals);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            
        }
        
        return this.getArea(values);
        }
        return 0;
    }

    @Override
    public String getName() {
        return "qaira_weighting_no";
    }
    
    public double getArea(HashMap<String,Double[]> values){
        double prevHeight=0;
        double prevBase=0;
        double height=0;
        double base=0;
        double area=0;
        List<String> lID=new ArrayList<String>(values.keySet());
        for (int i = 0; i < lID.size(); i++) {
            Double vals[]= values.get(lID.get(i));
            height=vals[1];
            base=vals[0];
            area=area+height*base;
            if(prevHeight!=0 && prevBase!=0){
                area=area+(height-prevHeight)*prevBase/2;
            }
            prevBase=base;
            prevHeight=height;            
        }
        if(prevHeight!=0 && prevBase!=0){
                area=area+(height-prevHeight)*prevBase/2;
            }
        return area;
    }
    
    public HashMap<String,Double> getDistances(double latIni,double lonIni, double latEnd, double lonEnd){
        HashMap<String,Double> hm=new HashMap<>();
        HashMap<String,double[]> cells=new HashMap<String,double[]>();
        String consulta="SELECT \"idcell\",\"midLat\"-(0.001*100/222) as southLat, \"midLat\"+(0.001*100/222) as northLat, \"midLon\"-(0.001*100/222) as westLon , \"midLon\"+(0.001*100/222) as eastLon FROM mydb.cellsdata where ?>=(\"midLon\"-(0.001*100/222)) and ?<=(\"midLon\"+(0.001*100/222)) and ?<=(\"midLat\"+(0.001*100/222)) and ?>=(\"midLat\"-(0.001*100/222)) order by idcell;";
        try {
            PreparedStatement ps= this.con.prepareStatement(consulta,ResultSet.TYPE_SCROLL_SENSITIVE, 
                        ResultSet.CONCUR_UPDATABLE);
            ps.setDouble(1, Double.min(lonIni, lonEnd));
            ps.setDouble(2,Double.max(lonIni, lonEnd));
            ps.setDouble(4, Double.min(latIni, latEnd));
            ps.setDouble(3, Double.max(latIni, latEnd));
            
            ResultSet rs = ps.executeQuery();
            if(!rs.next()){
                return hm;
            }
            rs.beforeFirst();
            while(rs.next()){
                double westBound=rs.getDouble("westLon");
                double eastBound=rs.getDouble("eastLon");
                double northBound=rs.getDouble("northLat");
                double southBound=rs.getDouble("southLat");
                String idCell=rs.getString("idcell");
                double boundaries[]=new double[4];
                boundaries[0]=southBound;
                boundaries[1]=westBound;
                boundaries[2]=northBound;
                boundaries[3]=eastBound;
                cells.put(idCell, boundaries);                
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        hm = this.calcDist(latIni,lonIni,latEnd,lonEnd,cells);
        return hm;
    }
    public HashMap<String,Double> calcDist(double latIni,double lonIni, double latEnd, double lonEnd, HashMap<String,double[]> cells){
        HashMap<String,Double> hm=new HashMap<>();
        //always start by the node that is closest to the norht and west
        // to iterate the cells from db correctly
        if(!(latIni>latEnd)){
            //if end in closer to the north bound start by end
            double tempLat=latIni;
            double tempLon=lonIni;
            latIni=latEnd;
            lonIni=lonEnd;
            latEnd=tempLat;
            lonEnd=tempLon;
        }
        double auxLat=latIni;
        double auxLon=lonIni;
        double prevLat=0;
        double prevLon=0;
        //slope
        String id="";
        double m= (latEnd-latIni)/(lonEnd-lonIni);
        // lon is X axis and Lat is Y axis
        // Y=mx+b
        double b=latIni-(m*lonIni);
        boolean notFound=true;
        String direction="";
        Iterator It=cells.entrySet().iterator();
        while(It.hasNext() && notFound){
            Map.Entry pair=(Map.Entry)It.next();
            id=(String) pair.getKey();
            double[] boundaries=(double[]) pair.getValue();
            if (auxLat>=boundaries[0] && auxLat<=boundaries[2] && auxLon>=boundaries[1] && auxLon<=boundaries[3]) {
                notFound=false;
                if ((boundaries[1]*m)+b>=boundaries[0] && (boundaries[1]*m)+b<=boundaries[2]) {//intercepts westbound
                    direction="west";
                    prevLat=auxLat;
                    prevLon=auxLon;
                    auxLat=(boundaries[1]*m)+b;
                    auxLon=boundaries[1];
                    continue;
                }
                if((boundaries[3]*m)+b>=boundaries[0] && (boundaries[3]*m)+b<=boundaries[2]){
                    direction="east";
                    prevLat=auxLat;
                    prevLon=auxLon;
                    auxLat=(boundaries[3]*m)+b;
                    auxLon=boundaries[3];
                    continue;
                    
                }
                if(((boundaries[0]-b)/m)>=boundaries[1] && ((boundaries[0]-b)/m)<=boundaries[3]){
                    direction="south";
                    prevLat=auxLat;
                    prevLon=auxLon;
                    auxLat=boundaries[0];
                    auxLon=((boundaries[0]-b)/m);
                    continue;
                
            }
                if(((boundaries[2]-b)/m)>=boundaries[1] && ((boundaries[2]-b)/m)<=boundaries[3]){
                    direction="north";
                    prevLat=auxLat;
                    prevLon=auxLon;
                    auxLat=boundaries[2];
                    auxLon=((boundaries[2]-b)/m);
                    continue;
                
            }
                
                
            }
        }
        String ID="";
        hm.put(id,this.getDistanceFromLatLonInM(prevLat,prevLon,auxLat,auxLon));
        while(!(Math.abs(auxLat-latEnd)<=0.0003 && Math.abs(auxLon-lonEnd)<=0.0003)){// Until reached the last note
            if (direction.equalsIgnoreCase("south")) {
                String split[]=id.split("_");
                int i= Integer.parseInt(split[0]);
                int j= Integer.parseInt(split[1]);
                i=i+1;
                ID= String.format("%02d",i)+"_"+String.format("%02d",j);
            }
            if(direction.equalsIgnoreCase("north")){
                String split[]=id.split("_");
                int i=Integer.parseInt(split[0]);
                int j=Integer.parseInt(split[1]);
                i=i-1;
                ID= String.format("%02d",i)+"_"+String.format("%02d",j);                
            }
            if(direction.equalsIgnoreCase("east")){
                String split[]=id.split("_");
                int i=Integer.parseInt(split[0]);
                int j=Integer.parseInt(split[1]);
                j=j+1;
                ID= String.format("%02d",i)+"_"+String.format("%02d",j); 
            }
            if(direction.equalsIgnoreCase("west")){
                String split[]=id.split("_");
                int i=Integer.parseInt(split[0]);
                int j=Integer.parseInt(split[1]);
                j=j-1;
                ID= String.format("%02d",i)+"_"+String.format("%02d",j); 
            }
            double boundar[]= (double[]) cells.get(ID);
            if(!(boundar==null || boundar.length==0)){
                
                if ((boundar[1]*m)+b>=boundar[0] && (boundar[1]*m)+b<=boundar[2]) {//intercepts westbound
                     direction="west";
                     prevLat=auxLat;
                     prevLon=auxLon;
                     auxLat=(boundar[1]*m)+b;
                     auxLon=boundar[1];

                    }
                    if((boundar[3]*m)+b>=boundar[0] && (boundar[3]*m)+b<=boundar[2]){
                        direction="east";
                        prevLat=auxLat;
                        prevLon=auxLon;
                        auxLat=(boundar[3]*m)+b;
                        auxLon=boundar[3];

                    }
                    if(((boundar[0]-b)/m)>=boundar[1] && ((boundar[0]-b)/m)<=boundar[3]){
                        direction="south";
                        prevLat=auxLat;
                        prevLon=auxLon;
                        auxLat=boundar[0];
                        auxLon=((boundar[0]-b)/m);

                }
                    if(((boundar[2]-b)/m)>=boundar[1] && ((boundar[2]-b)/m)<=boundar[3]){
                        direction="north";
                        prevLat=auxLat;
                        prevLon=auxLon;
                        auxLat=boundar[2];
                        auxLon=((boundar[2]-b)/m);

                }
            }
                if(((lonEnd*m)+b-latEnd)<=0.0001){ // it is the last node
                    prevLat=auxLat;
                    prevLon=auxLon;
                    auxLat=latEnd;
                    auxLon=lonEnd;
                }
                hm.put(ID, this.getDistanceFromLatLonInM(prevLat, prevLon, auxLat, auxLon));
            
        }
        
        return hm;
       
    }
    public double getDistanceFromLatLonInM(double lat1,double lon1,double lat2,double lon2){
        int R = 6371;
        double dLat = Math.toRadians(lat2-lat1);
        double dLon = Math.toRadians(lon2-lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c *1000;
        return d;
    }
}
