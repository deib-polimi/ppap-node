package it.polimi.deib.ppap.node;

import it.polimi.deib.ppap.node.commons.NormalDistribution;
import it.polimi.deib.ppap.node.commons.Utils;
import it.polimi.deib.ppap.node.services.Service;
import it.polimi.deib.ppap.node.services.ServiceRequest;

import java.io.IOException;

class TestLauncher {

    public static void main(String[] args) throws InterruptedException, IOException {

        NodeFacade facade = new NodeFacade("1", 8192, 9000, 0.9f, true);
        facade.setLogger(Utils.getLogger("exp1.log"));

        Service one = new Service("1", 128, 120, 90);
        Service two = new Service("2", 256, 100, 90);
        one.setTargetAllocation(32);
        two.setTargetAllocation(16);
        facade.addService(one);
        facade.addService(two);
        facade.start();

        Thread t1 = new Thread(executeRequests(facade, 1000, one));
        Thread t2 = new Thread(executeRequests(facade, 1000, two));

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        facade.stop();
        System.out.println("END");

    }


    private static Runnable executeRequests(NodeFacade facade, long num, Service service){
        return () -> {

            NormalDistribution n = Utils.getNormalDistribution(service.getRT()*0.8, service.getRT()*0.8*0.1);

            System.out.println("PHASE 1: "+service);
            // stable system at the beginning
            for (int i = 0; i < 200; i++) {
                facade.execute(new ServiceRequest(service, (long) n.random()));
                try {
                    Thread.sleep((long) service.getRT());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("PHASE 2: "+service);
            // decreasing inter-arrival rate
            for (int i = 0; i < num/3; i++) {
                facade.execute(new ServiceRequest(service, (long) n.random()));
                try {
                    Thread.sleep((long) (n.random()*0.8));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("PHASE 3: "+service);
            // peak inter-arrival rate
            for (int i = 0; i < num/3; i++) {
                facade.execute(new ServiceRequest(service, (long) n.random()));
                try {
                    Thread.sleep((long) (n.random()*0.3));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("PHASE 4: "+service);
            // decreasing inter-arrival rate
            for (int i = 0; i < num/3; i++) {
                facade.execute(new ServiceRequest(service, (long) n.random()));
                try {
                    Thread.sleep((long) (n.random()*0.7));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }


            System.out.println("END: "+service);

        };

    }
}
