package com.bitquest.bitquest;

import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.SystemUtils;
import org.bukkit.Bukkit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.Hashtable;

/**
 * Created by cristian on 12/15/15.
 */
public class Wallet {
    public int balance;
    public int confirmedBalance;
    public int unconfirmedBalance;
    public JSONObject jsonobj;
    
    public Wallet(String address,String privatekey) {
        this.address=address;
        this.privatekey=privatekey;
    }
    public Wallet(String address) {
        this.address=address;
    }
    public String address=null;
    private String privatekey=null;
    
    int balance() {
        this.updateBalance();
        return this.balance;
    }
    

    
    public int getBlockchainHeight() {
        JSONObject jsonobj = this.makeBlockCypherCall("https://api.blockcypher.com/v1/btc/main");
        return ((Number) jsonobj.get("height")).intValue();
    }
    
    public JSONObject getWalletStatus(String walletAddress) {
        this.jsonobj = this.makeBlockCypherCall("https://api.blockcypher.com/v1/btc/main/addrs/" + walletAddress);
        return this.jsonobj;
    }
    
    // @todo: make this just accept the endpoint name and (optional) parameters
    public JSONObject makeBlockCypherCall(String requestedURL) {
        JSONParser parser = new JSONParser();
        
        try {
            System.out.println("Making Blockcypher API call...");
            // @todo: add support for some extra params in this method (allow passing in an optional hash/dictionary/whatever Java calls it)?
            URL url = new URL(requestedURL + "?token=" + BitQuest.BLOCKCYPHER_API_KEY);

            System.out.println(url.toString());
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/1.22 (compatible; MSIE 2.0; Windows 3.1)");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            int responseCode = con.getResponseCode();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            
            System.out.println("JSON response: " + response.toString());
            
            return (JSONObject) parser.parse(response.toString());
        } catch (IOException e) {
            System.out.println("problem making API call");
            System.out.println(e);
            // Unable to call API?
        } catch (ParseException e) {
            // Bad JSON?
        }
        
        return new JSONObject(); // just give them an empty object
    }
    
    void updateBalance() {
        System.out.println("updating balance...");
        final JSONObject jsonobj = this.getWalletStatus(address);
        if (jsonobj.keySet().contains("final_balance")) {
            this.balance = ((Number) jsonobj.get("final_balance")).intValue();
            this.confirmedBalance = ((Number) jsonobj.get("balance")).intValue();
            this.unconfirmedBalance = ((Number) jsonobj.get("unconfirmed_balance")).intValue();
            JSONArray txs = (JSONArray) jsonobj.get("txrefs");
            System.out.println("Wallet " + address + " balances:");
            System.out.println("  Balance: " + this.balance + "SAT");
            System.out.println("  Confirmed Balance: " + this.confirmedBalance + "SAT");
            System.out.println("  Unconfirmed Spends: " + this.unconfirmedBalance + "SAT");
            
            Hashtable<String, Integer> tx_confs = new Hashtable<String, Integer>();
            Hashtable<String, Integer> tx_inputs = new Hashtable<String, Integer>();
            Hashtable<String, Integer> tx_outputs = new Hashtable<String, Integer>();
            
            for (int i = 0; i < txs.size(); i++) {
                JSONObject tx = (JSONObject) txs.get(i);
                String hash = ((String) tx.get("tx_hash")).toString();
                int confirmations = ((Number) tx.get("confirmations")).intValue();
                int input_n = ((Number) tx.get("tx_input_n")).intValue();
                int output_n = ((Number) tx.get("tx_output_n")).intValue();
                if (output_n < 0 || input_n >= 0) {
                    Integer orig_inp = 0;
                    if (tx_inputs.containsKey(hash)) {
                        orig_inp = tx_inputs.get(hash);
                    }
                    tx_inputs.put(hash, orig_inp + 1);
                }
                
                tx_confs.put(hash, confirmations);
                //System.out.println(hash + " = " + input_n + ", " + output_n + ", " + confirmations);
            }
            
            for (String hash: tx_inputs.keySet()) {
                Integer inputs = tx_inputs.get(hash);
                Integer confirmations = tx_confs.get(hash);
                System.out.println("  tx: " + hash + " has " + confirmations + " confirmation(s) and " + inputs + " input(s)");
            }
            
            
        } else {
            System.out.println("No reported final_balance in wallet: " + address);
        }

    }
    boolean transaction(int sat, Wallet wallet) throws IOException {
        JsonObject payload=new JsonObject();
        payload.addProperty("from_private",this.privatekey);
        payload.addProperty("to_address",wallet.address);
        payload.addProperty("value_satoshis",sat);
        URL url = new URL("https://api.blockcypher.com/v1/btc/main/txs/micro?token=" + BitQuest.BLOCKCYPHER_API_KEY);
        String inputLine = "";
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

        try {
            System.out.println("\nSending 'POST' request to URL : " + url);
            System.out.println("Payload : " + payload.toString());
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", "Mozilla/1.22 (compatible; MSIE 2.0; Windows 3.1)");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoOutput(true);
            OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
            out.write(payload.toString());
            out.close();
            int responseCode = con.getResponseCode();

            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode == 200||responseCode==201) {
                return true;
            } else {
                return false;
            }
        } catch(IOException ioe) {
            System.err.println("IOException: " + ioe);

            InputStream error = con.getErrorStream();

                int data = error.read();
                while (data != -1) {
                    //do something with data...
                    inputLine = inputLine + (char)data;
                    data = error.read();
                }
                error.close();


            System.out.println(inputLine);


            return false;
        }
        
    }
    boolean emailTransaction(int sat,String email) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IOException, ParseException {
        // create payload
        JSONObject obj = new JSONObject();
        obj.put("to", email);
        obj.put("currency", "SAT");
        obj.put("amount", sat);
        obj.put("subject", "BitQuest Withdrawal");
        obj.put("timestamp", System.currentTimeMillis() / 1000L);
        obj.put("unique_request_id", "BITQUEST" + System.currentTimeMillis());
        String data = obj.toString();
        int blocksize = 16;
        Bukkit.getLogger().info("blocksize: " + blocksize);
        int pad = blocksize - (data.length() % blocksize);
        Bukkit.getLogger().info("pad: " + pad);

        for (int i = 0; i < pad; i++) {
            data = data + "\0";
        }

        Bukkit.getLogger().info("payload: " + data);
        // encrypt payload
        String key = System.getenv("XAPO_APP_KEY");
        SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = null;

            cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            String epayload = new String(Base64.encodeBase64(cipher.doFinal(data.getBytes())));


            // post payload
            String urlstring = "https://api.xapo.com/v1/credit/";
            String query = "hash=" + URLEncoder.encode(epayload, "UTF-8") + "&appID=" + System.getenv("XAPO_APP_ID");

            URL url = new URL(urlstring);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

            //add reuqest header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", "Mozilla/1.22 (compatible; MSIE 2.0; Windows 3.1)");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            String urlParameters = query;

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            //print result
            Bukkit.getLogger().info(response.toString());
            JSONParser parser = new JSONParser();
            final JSONObject jsonobj = (JSONObject) parser.parse(response.toString());
            Bukkit.getLogger().info("---------- XAPO TRANSACTION END ------------");
        return true;
    }

}
