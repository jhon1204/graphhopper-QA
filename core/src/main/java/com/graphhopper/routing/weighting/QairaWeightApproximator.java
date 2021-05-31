/*
 * Copyright 2021 Jhon.
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
package com.graphhopper.routing.weighting;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.ini4j.Ini;
/**
 *
 * @author Jhon
 */
public class QairaWeightApproximator implements WeightApproximator{
    private final NodeAccess nodeAccess;
    private final Weighting weighting;
    private DistanceCalc distanceCalc = Helper.DIST_EARTH;
    private double toLat, toLon;
    private double contaminationTo=0;
    private String cellTo="";
    private double epsilon = 1;
    private Connection con;

    public QairaWeightApproximator(NodeAccess nodeAccess, Weighting weighting) {
        this.nodeAccess = nodeAccess;
        this.weighting = weighting;
        String realPath=System.getProperty("user.dir");
        //String fileName = "./sensors-config/" + MYSQL ".config";
        String fileName = realPath+"\\sensors-config\\MYSQL.config";
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
    public void setTo(int toNode) {
        toLat = nodeAccess.getLatitude(toNode);
        toLon = nodeAccess.getLongitude(toNode);
        String query= "SELECT \"idcell\",\"midLat\"-(0.001*100/222) as \"southLat\", \"midLat\"+(0.001*100/222) as \"northLat\", \"midLon\"-(0.001*100/222) as \"westLon\" , \"midLon\"+(0.001*100/222) as \"eastLon\" FROM mydb.cellsdata where ?>=(\"midLon\"-(0.001*100/222)) and ?<=(\"midLon\"+(0.001*100/222)) and ?<=(\"midLat\"+(0.001*100/222)) and ?>=(\"midLat\"-(0.001*100/222)) order by idcell;";
        try {
            PreparedStatement ps= this.con.prepareStatement(query);
            ps.setDouble(1, toLat);
            ps.setDouble(2, toLat);
            ps.setDouble(3, toLon);
            ps.setDouble(4, toLon);
            ResultSet rs = ps.executeQuery();
            if(!rs.next()){
                return;
            }
            this.cellTo=rs.getString("idcell");
            String query2="SELECT m.\"interpolatedValiue\" FROM mydb.interpolatedmetrics as m , mydb.pollutant as p   where m.\"idPollutant\"=p.\"idPollutant\" and  m.\"idinterpolation_algorithm\"=\"IDW\" and m.idcell=? and p.\"pollutantName\"=?;"; 
            PreparedStatement ps2=this.con.prepareStatement(query2);
            ps2.setString(1, this.cellTo);
            ps2.setString(2, this.weighting.getName().substring(this.weighting.getName().length()-2).toUpperCase());
            ResultSet rs2=ps2.executeQuery();
            if(!rs.next())
                return;
            this.contaminationTo=rs.getDouble("interpolatedValiue");
            
            
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public WeightApproximator setEpsilon(double epsilon) {
        this.epsilon = epsilon;
        return this;
    }

    @Override
    public WeightApproximator reverse() {
        return new QairaWeightApproximator(nodeAccess, weighting).setDistanceCalc(distanceCalc).setEpsilon(epsilon);
    }

    @Override
    public double getSlack() {
        return 0;
    }

    @Override
    public double approximate(int fromNode) {
        double fromLat = nodeAccess.getLatitude(fromNode);
        double fromLon = nodeAccess.getLongitude(fromNode);
        double weightFrom=0;
        String query= "SELECT \"idcell\",\"midLat\"-(0.001*100/222) as \"southLat\", \"midLat\"+(0.001*100/222) as \"northLat\", \"midLon\"-(0.001*100/222) as \"westLon\" , \"midLon\"+(0.001*100/222) as \"eastLon\" FROM mydb.cellsdata where ?>=(\"midLon\"-(0.001*100/222)) and ?<=(\"midLon\"+(0.001*100/222)) and ?<=(\"midLat\"+(0.001*100/222)) and ?>=(\"midLat\"-(0.001*100/222)) order by idcell;";
        try {
            String cellFrom="";
            PreparedStatement ps= this.con.prepareStatement(query);
            ps.setDouble(1, fromLat);
            ps.setDouble(2, fromLat);
            ps.setDouble(3, fromLon);
            ps.setDouble(4, fromLon);
            ResultSet rs = ps.executeQuery();
            if(!rs.next()){
                
                weightFrom= 0;
            }else{
                cellFrom=rs.getString("idcell");
                String query2="SELECT m.\"interpolatedValiue\" FROM mydb.interpolatedmetrics as m , mydb.pollutant as p   where m.\"idPollutant\"=p.\"idPollutant\" and  m.\"idinterpolation_algorithm\"=\"IDW\" and m.\"idcell\"=? and p.\"pollutantName\"=?;"; 
                PreparedStatement ps2=this.con.prepareStatement(query2);
                ps2.setString(1, cellFrom);
                ps2.setString(2, this.weighting.getName().substring(this.weighting.getName().length()-2).toUpperCase());
                ResultSet rs2=ps2.executeQuery();
                if(!rs.next())
                    weightFrom= 0 ;
                else
                weightFrom=rs.getDouble("interpolatedValiue");
            }
            
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        double dist2goal = distanceCalc.calcDist(toLat, toLon, fromLat, fromLon);
        double weight2goal = weighting.getMinWeight(dist2goal);
        weight2goal=weight2goal*weightFrom+((this.contaminationTo-weightFrom)*weight2goal/2);
        return weight2goal * epsilon;
    }

    @Override
    public WeightApproximator setDistanceCalc(DistanceCalc distanceCalc) {
        this.distanceCalc = distanceCalc;
        return this;
    }

    @Override
    public String toString() {
        return "qairapproximator";
    }
    
}
