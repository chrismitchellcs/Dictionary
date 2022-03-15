package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import javax.xml.crypto.Data;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    Socket dictionarySocket;
    PrintWriter out;
    BufferedReader in;
    BufferedReader stdIn;
    String fromServer;
    String fromUser;
    String code;


    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        // TODO Add your code here
        try {
            // Creating socket and reader and writers for the socket
            dictionarySocket = new Socket(host, port);
            out = new PrintWriter(dictionarySocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(dictionarySocket.getInputStream()));
            stdIn = new BufferedReader(new InputStreamReader(System.in));

            // reading the message from the server and handling it
            if ((fromServer = in.readLine()) != null) {
                System.out.println("Server: " + fromServer);
                if (fromServer.charAt(0) == '5' || fromServer.charAt(0) == '4') {
                    throw new DictConnectionException();
                }
            } else {
                throw new DictConnectionException();
            }
        } catch (UnknownHostException e) {
            throw new DictConnectionException();
        } catch (IOException e) {
            throw new DictConnectionException();
        }

    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        // sent quit message to server
        out.println("QUIT");
        try {
            // read reply from server and close the socket
            if ((fromServer = in.readLine()) != null) {
                System.out.println("Server: " + fromServer);
                dictionarySocket.close();
            }
        } catch (IOException e) {
            System.out.println("Closing Failed");
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        System.out.println("DEFINE " + database.getName() + " \"" + word + "\"");
        // send message to server requesting it to define a word from a database
        out.println("DEFINE " + database.getName() + " \"" + word + "\"");

        try {
            // read the reply from the server
            fromServer = in.readLine();
            //determines what to do with message based on reply
            code = fromServer.substring(0,3);
            int i = 0;
            int done;
            int defCount = 10;
            // gets number of definitions recieved to help parse
            if (code.equals("150")) {
                if (fromServer.charAt(5) != ' ') {
                    defCount = Integer.parseInt(fromServer.substring(4,6));
                } else {
                    defCount = Character.getNumericValue(fromServer.charAt(4));
                }
            }
            // returns empty set if no definitions
            if (fromServer.charAt(0) == '5') {
                return set;
            }

            // outer loop for each of the definition
            while (((fromServer = in.readLine()) != null) && (i < defCount)) {
                code = fromServer.substring(0,3);
                if (code.equals("151")) {
                    // finds name of the word we are defining and the database
                    Pattern p = Pattern.compile("\"(.*?)\"");
                    Matcher m = p.matcher(fromServer);
                    m.find();
                    String defName = m.group(1);
                    Pattern p2 = Pattern.compile("\" (.*?) \"");
                    Matcher m2 = p2.matcher(fromServer);
                    m2.find();
                    String databaseName = m2.group(1);
                    Definition def = new Definition(defName, databaseName);
                    String definition = "";
                    done = 0;
                    // inner loop which handles the parsing of the definition of each word
                    while ((done == 0) && ((fromServer = in.readLine()) != null)) {
                        if (!fromServer.isEmpty() && fromServer.charAt(0) == '.') {
                            done = 1;
                            i++;
                        } else {
                            definition = definition + "\r\n" + fromServer;
                        }
                    }
                    // adds the definition to the set
                    def.setDefinition(definition);
                    set.add(def);
                }
            }
        } catch (IOException e) {
            System.out.println("getDefinitions Failed");
            throw new DictConnectionException();
        }

        System.out.println("done");
        return set;
    }


    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();
        // send request to server
        out.println("MATCH " + database.getName() + " " + strategy.getName() + " \"" + word + "\"");
        try {
            // handles each matching strategy on each line
            while (((fromServer = in.readLine()) != null)  && (fromServer.charAt(0) != '2')) {
                // handles empty matchings
                if (fromServer.charAt(0) == '5') {
                    return set;
                }
                // finds match name and adds to the set
                Pattern p = Pattern.compile("\"(.*?)\"");
                Matcher m = p.matcher(fromServer);
                while(m.find())
                {
                    m.group(1);
                    set.add(m.group(1));
                }
            }
        } catch (IOException e) {
            throw new DictConnectionException();
        }
        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();
        //sends request to server to get databases
        out.println("SHOW DB");
        String name;
        try {
            // loops on all of the databases the server has given
            while (((fromServer = in.readLine()) != null) && (fromServer.charAt(0) != '2')) {
                // handles empty or non empty lists
                if (fromServer.charAt(0) == '1') {
                    System.out.println("Server: " + fromServer);
                }
                if (fromServer.charAt(0) == '5') {
                    System.out.println("Server: " + fromServer);
                    return databaseMap;
                } else {
                    if (fromServer.charAt(0) != '.') {
                        // gets name of database
                        name = fromServer.substring(0, fromServer.indexOf(" "));
                        // gets description of database
                        Pattern p = Pattern.compile("\"(.*?)\"");
                        Matcher m = p.matcher(fromServer);
                        while(m.find())
                        {
                            m.group(1);
                            // adds database to databaseMap
                            databaseMap.put(name, new Database(name, m.group(1)));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("SHOW DB FAILED");
        }

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        //sends request to server to get strategies
        out.println("SHOW STRAT");
        String name;
        try {
            while (((fromServer = in.readLine()) != null) && (fromServer.charAt(0) != '2')) {
                if (fromServer.charAt(0) == '1') {
                    System.out.println("Server: " + fromServer);
                }
                if (fromServer.charAt(0) == '5') {
                    System.out.println("Server: " + fromServer);
                    return set;
                } else {
                    if (fromServer.charAt(0) != '.') {
                        // gets name of matching strategy
                        name = fromServer.substring(0, fromServer.indexOf(" "));

                        // gets description of the matching strategy
                        Pattern p = Pattern.compile("\"(.*?)\"");
                        Matcher m = p.matcher(fromServer);
                        while(m.find())
                        {
                            m.group(1);
                            set.add(new MatchingStrategy(name, m.group(1)));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("SHOW STRAT FAILED");
            throw new DictConnectionException();
        }


        return set;
    }
}
