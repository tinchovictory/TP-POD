package ar.edu.itba.pod.client.Queries.Query3;

import ar.edu.itba.pod.Airport;
import ar.edu.itba.pod.Movement;
import ar.edu.itba.pod.Query3.AirportMovementsCombinerFactory;
import ar.edu.itba.pod.Query3.AirportMovementsMapper;
import ar.edu.itba.pod.Query3.AirportMovementsReducerFactory;
import ar.edu.itba.pod.Query3.OaciTuple;
import ar.edu.itba.pod.client.Printer;
import ar.edu.itba.pod.client.Queries.Query;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IList;
import com.hazelcast.mapreduce.Job;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.KeyValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class Query3 implements Query {
    private IList<Movement> movements;
    private HazelcastInstance hz;
    private Printer printer;

    public Query3(IList<Movement> movements, HazelcastInstance hz,Printer printer) {
        this.movements = movements;
        this.hz = hz;
        this.printer = printer;
    }

    @Override
    public void run() throws InterruptedException, ExecutionException {
        /* Create Query 3 Job */
        JobTracker jobTracker = hz.getJobTracker("Query3");

        /* Get movements group amount
         * Key is oaci tuple, Value is amount of movements */
        Map<OaciTuple, Integer> airportMovements = getAirportMovements(jobTracker, movements);

        /* Get query output */
        List<QueryOutputRow> queryOutput = getQueryOutput(airportMovements);

        /* Print query output */
        printOutput(queryOutput);
    }

    private Map<OaciTuple, Integer> getAirportMovements(JobTracker jobTracker, IList<Movement> hzMovement) throws InterruptedException, ExecutionException {
        /* Key is collection name */
        KeyValueSource<String, Movement> source = KeyValueSource.fromList(hzMovement);
        Job<String, Movement> job = jobTracker.newJob(source);

        /* Run map reduce */
        ICompletableFuture<Map<OaciTuple, Integer>> future = job
                .mapper(new AirportMovementsMapper())
                .combiner(new AirportMovementsCombinerFactory())
                .reducer(new AirportMovementsReducerFactory())
                .submit();

        /* Get map reduce output */
        return future.get();
    }

    private List<QueryOutputRow> getQueryOutput(Map<OaciTuple, Integer> airportMovements) {
        List<QueryOutputRow> queryOutput = new ArrayList<>();

        for(OaciTuple oaciTuple : airportMovements.keySet()) {
            Integer dir = airportMovements.get(oaciTuple);
            OaciTuple opositeTuple = new OaciTuple(oaciTuple.getDestinationOaci(), oaciTuple.getOriginOaci());
            Integer opositeDir = airportMovements.get(opositeTuple);
            if(opositeDir == null) {
                opositeDir = 0;
            }

            queryOutput.add(new QueryOutputRow(oaciTuple, dir, opositeDir));

            /* Fix missing tuple when opositeDir is 0 */

            if(opositeDir == 0) {
                queryOutput.add(new QueryOutputRow(opositeTuple, opositeDir, dir));
            }
        }

        /* Sort query output */
        queryOutput.sort((QueryOutputRow o1, QueryOutputRow o2) -> {
            int originOaciCmp = o1.oaciTuple.getOriginOaci().compareTo(o2.oaciTuple.getOriginOaci());
            if(originOaciCmp == 0) {
                return o1.oaciTuple.getDestinationOaci().compareTo(o2.oaciTuple.getDestinationOaci());
            }

            return originOaciCmp;
        });

        return queryOutput;
    }

    private void printOutput(List<QueryOutputRow> queryOutput) {
        printer.appendToFile("Origen;Destino;Origen->Destino;Destino->Origen\n");
        for(QueryOutputRow row : queryOutput) {
            printer.appendToFile(row+"\n");
        }
    }

    private class QueryOutputRow {
        private final OaciTuple oaciTuple;
        private final int originDestination;
        private final int destinationOrigin;

        public QueryOutputRow(OaciTuple oaciTuple, int originDestination, int destinationOrigin) {
            this.oaciTuple = oaciTuple;
            this.originDestination = originDestination;
            this.destinationOrigin = destinationOrigin;
        }

        @Override
        public String toString() {
            return oaciTuple.getOriginOaci() + ";" + oaciTuple.getDestinationOaci() + ";" + originDestination + ";" + destinationOrigin;
        }
    }
}
