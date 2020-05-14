package supervisor.balancer.estimation;

import supervisor.util.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class BinaryStochasticBanditProblem {

    private final AtomicInteger hitc=new AtomicInteger(1);
    private final AtomicInteger updatec=new AtomicInteger(1);
    private final AtomicInteger totalc=new AtomicInteger(1);

    private final double base;

    public BinaryStochasticBanditProblem(double base){
        this.base = base;
    }

    public void hit(){
        hitc.addAndGet(1);
        totalc.addAndGet(1);
    }

    public void update(){
        updatec.addAndGet(1);
        totalc.addAndGet(1);
    }

    public void setUpdate(long n){
        int old = updatec.getAndSet((int)n);
        totalc.addAndGet((int)n - old);
    }

    void revertUpdate(){
        totalc.decrementAndGet();
        updatec.decrementAndGet();
    }
    public int getHit(){
        return hitc.get();
    }

    double hitScore(){
        return base + Math.sqrt(2*Math.log(totalc.get())/hitc.get());
    }

    double updateScore(){
        return Math.sqrt(2*Math.log(totalc.get())/updatec.get());
    }

    public boolean shouldUpdate(){
        double a = hitScore();
        double b = updateScore();
        boolean veredict = (b >= a);

        if(veredict)
            update();
        else
            hit();

        return veredict;
    }

}
