/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package supervisor.storage.remote;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import supervisor.balancer.CMonitor;
import supervisor.server.Count;
import supervisor.util.Logger;

/**
 *
 * @author JARM
 */
public class Estimator {

    static double estimateBranchesTaken(String key, String solver, String un, String board, CachedRemoteStorage cachedRemoteStorage) {
        double est = 0.0;
        try {
            Map<String, String> v = cachedRemoteStorage.get(key);
            if (v == null) {
                // was never performed.
                if (cachedRemoteStorage.cachecontains(solver, board)) {
                    // has classe element.
                    Set<String> ks = cachedRemoteStorage.cachetree.get(solver).get(board);
                    //Logger.log(">>>>>>>> contains: " + ks);
                    if (ks.size() > 1 && !solver.equals("DLX")) {
                        // more than 2 sizes.
                        int target = Integer.parseInt(un);
                        int before = Integer.MIN_VALUE;
                        int after = Integer.MAX_VALUE;
                        for (String k : ks) {
                            int candidate = Integer.parseInt(k);
                            if (candidate < target) {
                                if (before < candidate) {
                                    before = candidate;
                                }
                            } else {
                                if (candidate < after) {
                                    after = candidate;
                                }
                            }
                        }
                        if (before == Integer.MIN_VALUE || after == Integer.MAX_VALUE) {
                            // chooses closet.
                            int choice;
                            if (before == Integer.MIN_VALUE) {
                                choice = after;
                            } else {
                                choice = before;
                            }
                            Count c = Count.fromString(cachedRemoteStorage.cache.get(solver + ":" + choice + ":" + board).get("Count"));
                            est = c.mean() + Math.sqrt(c.var());
                        } else {
                            // does linear interpolation.
                            Count cbefore = Count.fromString(cachedRemoteStorage.cache.get(solver + ":" + before + ":" + board).get("Count"));
                            Count cafter = Count.fromString(cachedRemoteStorage.cache.get(solver + ":" + after + ":" + board).get("Count"));
                            est = linint((double) before, cbefore.mean(), (double) after, cafter.mean(), (double) target);
                            est += Math.sqrt(Math.max(cafter.var(), cbefore.var()));
                        }
                    } else {
                        String kclose = ks.iterator().next();
                        v = cachedRemoteStorage.get(solver + ":" + kclose + ":" + board);
                        Count c = Count.fromString(v.get("Count"));
                        est = c.mean() + Math.sqrt(c.var());
                    }
                } else {
                    //Logger.log(">>>>>>>> does not contain");
                    // does not have class elements.
                    est = 4000.0;
                }
            } else {
                // was already performed.
                Count c = Count.fromString(v.get("Count"));
                est = c.mean() + Math.sqrt(c.var());
            }
        } catch (IOException e) {
            Logger.log(e.toString());
        } catch (ClassNotFoundException e) {
            Logger.log(e.toString());
        }
        return est;
    }

    public static double estimate(String key, CachedRemoteStorage cachedRemoteStorage) {
        String[] sv = key.split(":");
        String solver = sv[0];
        String un = sv[1];
        String board = sv[2] + ":" + sv[3];
        double est = estimateBranchesTaken(key, solver, un, board, cachedRemoteStorage);
        switch (solver) {
            case "BFS":
                est = est * 12.88852179 + 14068.78484095;
                break;
            case "CP":
                est = est * 14.16131419 + 19312.86569091;
                break;
            case "DLX":
                est = est * 24.39689662 - 1392680.19952047;
                break;
        }
        if (est < 0) {
            est = 400000 * 24.39689662 - 1392680.19952047;
        }
        return est;
    }

    static double linint(double x0, double y0, double x1, double y1, double x) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }
}
