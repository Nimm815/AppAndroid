package vn.edu.taskmind.task.api;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
/** Caller holds the device row lock for the entire transaction, including all entity writes. */
@Component @Profile("dev")
public class DeviceWorkspaceStore {
 final JdbcTemplate db;final NativeIdentity user;
 public DeviceWorkspaceStore(JdbcTemplate db,NativeIdentity user){this.db=db;this.user=user;}
 public long lock(String device){
  UUID.fromString(device);
  db.update("INSERT IGNORE INTO device_workspaces(UserID,DeviceID) VALUES (?,?)",user.id(),device);
  return db.queryForObject("SELECT Revision FROM device_workspaces WHERE UserID=? AND DeviceID=? FOR UPDATE",Long.class,user.id(),device);
 }
 public Integer id(String device,String kind,String local){
  var ids=db.queryForList("SELECT RemoteID FROM device_records WHERE UserID=? AND DeviceID=? AND Kind=? AND LocalID=?",Integer.class,user.id(),device,kind,local);
  return ids.isEmpty()?null:ids.get(0);
 }
 public int create(String device,String kind,String local,String insert,Object... values){
  db.update(insert,values);int remote=db.queryForObject("SELECT LAST_INSERT_ID()",Integer.class);
  db.update("INSERT INTO device_records(UserID,DeviceID,Kind,LocalID,RemoteID) VALUES (?,?,?,?,?)",user.id(),device,kind,local,remote);return remote;
 }
 public Map<String,Integer> records(String device,String kind){
  Map<String,Integer> result=new HashMap<>();db.query("SELECT LocalID,RemoteID FROM device_records WHERE UserID=? AND DeviceID=? AND Kind=?",rs->{result.put(rs.getString(1),rs.getInt(2));},user.id(),device,kind);return result;
 }
 public void unmap(String device,String kind,String local){db.update("DELETE FROM device_records WHERE UserID=? AND DeviceID=? AND Kind=? AND LocalID=?",user.id(),device,kind,local);}
 public void finish(String device,long revision,String payload){db.update("UPDATE device_workspaces SET Revision=?,Payload=? WHERE UserID=? AND DeviceID=?",revision,payload,user.id(),device);}
 public Map<String,Object> read(String device){UUID.fromString(device);var result=db.queryForList("SELECT Revision AS revision, CAST(Payload AS CHAR CHARACTER SET utf8mb4) AS payload FROM device_workspaces WHERE UserID=? AND DeviceID=?",user.id(),device);if(result.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chưa có dữ liệu thiết bị");return result.get(0);}
 public static void unique(Collection<String> keys){if(new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("Duplicate local ID");}
}
