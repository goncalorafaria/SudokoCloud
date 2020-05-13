
package supervisor.balancer.estimation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import supervisor.util.Logger;


public class UpdatePolicy {

    static class StochasticBaditProblem {
        private final AtomicInteger hitc=new AtomicInteger(1);
        private final AtomicInteger updatec=new AtomicInteger(1);
        private final AtomicInteger totalc=new AtomicInteger(1);

        private final double base;

        StochasticBaditProblem(double base){
            this.base = base;
        }

        void hit(){
            hitc.addAndGet(1);
            totalc.addAndGet(1);
        }

        void update(){
            updatec.addAndGet(1);
            totalc.addAndGet(1);
        }

        double hitScore(){
            return base + Math.sqrt(2*Math.log(totalc.get())/hitc.get());
        }

        double updateScore(){
            return Math.sqrt(2*Math.log(totalc.get())/updatec.get());
        }

        boolean shouldUpdate(){
            double a = hitScore();
            double b = updateScore();

            return (b >= a);
        }
    }
    
    /* Stores the update Policies for each key (aka puzzle request):
        The policy of a key decides if the cache for that key should be updated
        with the remote storage value or not. */
    private ConcurrentHashMap<String, StochasticBaditProblem> policies =
            new ConcurrentHashMap<>();
    

    void addPolicy(String key) {
        policies.put(key, new StochasticBaditProblem(0.1));
    }

    boolean updatePolicy(
            Map<String,String> value,
            String key){

            StochasticBaditProblem ucb = policies.get(key);
            // sqrt( 2 * log(total)/ refreshc ) >= 1
            if( ucb.shouldUpdate() ){
                // updating cache.
                ucb.update();
                Logger.log("Updating the cache for:  " + key);
                return true;
            }else{
                ucb.hit();
                Logger.log("Hit on cache with: " + key);
                return false;
            }
    }
    
    void clear() {
        policies.clear();
    }
    
}
