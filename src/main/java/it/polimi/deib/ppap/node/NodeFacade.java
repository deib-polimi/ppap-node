package it.polimi.deib.ppap.node;


import it.polimi.deib.ppap.node.control.Planner;
import it.polimi.deib.ppap.node.control.PlannerController;
import it.polimi.deib.ppap.node.history.History;
import it.polimi.deib.ppap.node.history.HistoryData;
import it.polimi.deib.ppap.node.monitoring.Monitor;
import it.polimi.deib.ppap.node.monitoring.MonitoringData;
import it.polimi.deib.ppap.node.services.Service;
import it.polimi.deib.ppap.node.services.ServiceExecutor;
import it.polimi.deib.ppap.node.services.ServiceRequest;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;


public class NodeFacade {


    public static final String LOG_HEADER = "nId,ts,sId,sla,rt,rq,al";

    private String nodeId;
    private long memory;
    private long controlPeriod;

    private Map<Service, ServiceExecutor> services = new HashMap<>();
    private PlannerController controller;
    private Monitor monitor = new Monitor();
    private Timer timer = new Timer();
    private History history = new History();
    private ExecutorService loggerService = Executors.newSingleThreadExecutor();
    private Logger logger;
    private Optional<TickListener> tickListener;
    private int lastThreads = -1;
    private int lastAllocation = -1;
    private int currentAllocation = 0;
    private boolean control, verbose = true;

    public NodeFacade(
            String nodeId,
            long memory,
            long controlPeriodMillis,
            float alpha,
            boolean control){
        this.nodeId = nodeId;
        this.memory = memory;
        this.controlPeriod = controlPeriodMillis;
        this.tickListener = Optional.empty();
        controller = new PlannerController(alpha, memory);
        this.control = control;
    }

    public void setVerbose(boolean verbose){
        this.verbose = verbose;
    }

    public void start(){
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        }, controlPeriod, controlPeriod);
    }

    public void setLogger(Logger logger){
        this.logger = logger;
    }

    public synchronized void addService(Service service){
        ServiceExecutor serviceExecutor = new ServiceExecutor(service, memory, monitor);
        this.services.put(service, serviceExecutor);
        controller.addService(service);
        this.monitor.addService(service);
        this.history.addService(service);
        serviceExecutor.start();
    }

    public synchronized void removeService(Service service){
        ServiceExecutor executor = services.get(service);
        executor.shutdownNow();
        controller.removeService(service);
        monitor.removeService(service);
        this.history.removeService(service);
        services.remove(service);
    }

    public void setTargetAllocation(Service service, float allocation){
        for (Service s : services.keySet()){
            if (s.equals(service)) {
                s.setTargetAllocation(allocation);
                break;
            }
        }


    }

    public void stop(){
        timer.cancel();
        for(Service s : new HashSet<>(services.keySet())){
            removeService(s);
        }
        loggerService.shutdownNow();
    }

    public void execute(ServiceRequest request) {
        if (services.containsKey(request.getService()))
            this.services.get(request.getService()).execute(request);
        //else throw new ServiceNotFoundException();
    }

    public class ServiceNotFoundException extends RuntimeException {}
    public void setTickListener(TickListener tickListener){
        this.tickListener = Optional.of(tickListener);
    }

    // retrieve and clear history of a service
    public List<HistoryData> getHistory(Service service){
        return history.read(service);
    }

    public float getLastOptimalAllocation(Service service){
        return controller.getLastOptimalAllocation(service);
    }

    public static float getStaticAllocation(float interArrivalMillis, float targetResponseTimeMillis, float controlPeriodMillis){
        float req = controlPeriodMillis/interArrivalMillis;
        return Planner.computeStaticAllocation(req, targetResponseTimeMillis);
    }

    public boolean isServing(Service service) {
        return this.services.containsKey(service);
    }

    private void tick(){
        currentAllocation = 0;
        Map<Service, MonitoringData> monitoring = monitor.read();
        Map<Service, Float> allocations = controller.control(monitoring, control);
        allocations.forEach((service, allocation) -> {
            services.get(service).setSize(allocation.intValue());
            currentAllocation += allocation.intValue();
        });

        allocations.forEach((service, allocation) -> history.addData(service, monitoring.get(service), allocation, getLastOptimalAllocation(service)));

        long ts = System.currentTimeMillis();

        loggerService.execute( () -> {
            if (logger != null){
                for (Service s : monitoring.keySet()){
                    MonitoringData data = monitoring.get(s);
                    logger.info(nodeId+","+ts+","+s+","+s.getRT()+","+data.getResponseTime()+","+data.getRequests()+","+allocations.get(s));
                }
            }
        });
        tickListener.ifPresent((l) -> l.afterTick());

        lastThreads = Thread.activeCount();
        lastAllocation = currentAllocation;

    }

    public interface TickListener{
        public void afterTick();
    }

}