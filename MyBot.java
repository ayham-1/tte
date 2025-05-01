import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import page.codeberg.terratactician_expandoria.*;
import page.codeberg.terratactician_expandoria.bots.*;
import page.codeberg.terratactician_expandoria.world.CubeCoordinate;
import page.codeberg.terratactician_expandoria.world.tiles.*;
import page.codeberg.terratactician_expandoria.world.tiles.Tile.TileType;

/*
 * Observations:
 *  - only one placement of a tile is allowed per turn
 *  - memory is more abundant than time!
 *
 * TODOs:
 * - [x] separate the different tile type placements into functions.
 * - [ ] track the usable non isolated tiles ourselves, so that iteration is
 * fast
 * - [ ] track the groups of different tiles together ourselves
 * - [ ] find a way to integrate the needs of the tracked groups surrounding the
 * considered usable tile when picking which usable tile to use
 * - [ ] investigate more abstract considerations for groups:
 *    - [ ] prefer packed groups
 *    - [ ] prefer patterns of groups
 * */

public class MyBot extends ChallengeBot {

  @Override
  public int getMatrikel() {
    return 251435;
  }

  @Override
  public String getStudentName() {
    return "Ayham A.F.";
  }

  @Override
  public String getName() {
    return "Bob the Bot the Builder";
  }

  World world;
  Controller controller;

  boolean is_first = true;
  @Override
  public void executeTurn(World world, Controller controller) {
    this.world = world;
    this.controller = controller;

    var current = world.getResources();
    var target = world.getTargetResources();
    var growth = world.getResourcesRate();
    var time_left = world.getRoundTime();

    boolean money =
        (current.money + (growth.money * time_left)) >= target.money;

    boolean food = (current.food + (growth.food * time_left)) >= target.food;

    boolean materials = (current.materials + (growth.materials * time_left)) >=
                        target.materials;

    double perc_money = current.money / target.money;
    double perc_food = current.food / target.food;
    double perc_materials = current.materials / target.materials;

    // System.out.println("[current] money=" + current.money + " food=" +
    //                    current.food + " materials=" + current.materials);

    // System.out.println("[target] money=" + target.money + " food=" +
    //                    target.food + " materials=" + target.materials);
    // System.out.println("[growth] money=" + growth.money + " food=" +
    //                    growth.food + " materials=" + growth.materials);
    // System.out.println("[perc] money=" + String.format("%.3f", perc_money) +
    //                    " food=" + String.format("%.3f", perc_food) +
    //                    " materials=" + String.format("%.3f",
    //                    perc_materials));

    if (world.getHand().isEmpty() && world.getRedrawTime() <= 0) {
      controller.redraw();
      return;
    }

    for (var reward : world.getRewards()) {
      controller.collectReward(reward.getCoordinate());
      if (!controller.actionPossible())
        break;
    }
    if (!controller.actionPossible())
      return;

    if (world.getHand().isEmpty() && world.getRedrawTime() > 5) {
      // determine if we need to redraw since we are not hitting the target
      // resources
      var cost = world.getRedrawCosts();
      boolean redrawable = current.money >= cost.money &&
                           current.food >= current.food &&
                           current.materials >= cost.materials;
      if (redrawable)
        if ((!money || !food || !materials) && perc_money > 0.75 &&
            perc_food > 0.75 && perc_materials > 0.75)
          controller.redraw();
    }

    for (var card : world.getHand()) {
      if (!controller.actionPossible())
        return;

      if (is_first) {
        controller.placeTile(new CubeCoordinate());
        is_first = false;
        return;
      }

      if (card == TileType.Marketplace) {
        this.place_marketplace();
      } else if (card == TileType.Grass) {
        this.place_grass();
      } else if (card == TileType.StoneHill) {
        this.place_stonehill();
      } else if (card == TileType.StoneMountain) {
        this.place_stonemountain();
      } else if (card == TileType.StoneRocks) {
        this.place_stonerocks();
      } else if (card == TileType.Wheat) {
        this.place_wheat();
      } else if (card == TileType.Forest) {
        this.place_forest();
      } else if (card == TileType.DoubleHouse) {
        this.place_doublehouse();
      } else if (card == TileType.SmallHouse) {
        this.place_smallhouse();
      } else if (card == TileType.Windmill) {
        this.place_windmill();
      } else if (card == TileType.Beehive) {
        this.place_beehive();
      } else if (card == TileType.StoneQuarry) {
        this.place_stonequarry();
      } else if (card == TileType.Moai) {
        this.place_moai();
      }
    }
  }

  void place_marketplace() {
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        controller.placeTile(TileType.Marketplace, cplacable);
        return;
      }
    }
  }

  void place_grass() {
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        controller.placeTile(TileType.Grass, cplacable);
        return;
      }
    }
  }

  void place_stonehill() {
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        controller.placeTile(TileType.StoneHill, cplacable);
        return;
      }
    }
  }

  void place_stonemountain() {
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        controller.placeTile(TileType.StoneMountain, cplacable);
        return;
      }
    }
  }

  void place_stonerocks() {
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        controller.placeTile(TileType.StoneRocks, cplacable);
        return;
      }
    }
  }

  void place_wheat() {
    // beside other wheats
    CubeCoordinate max_cplacable = null;
    int max_gc = 0; // max is less than 8 here
    ArrayList<Tile> map_reversed = new ArrayList<>();
    for (var t : world.getMap())
      map_reversed.add(t);
    Collections.reverse(map_reversed);

    for (var placed_tile : map_reversed) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        if (max_cplacable == null)
          max_cplacable = cplacable;

        for (var cneighbor : cplacable.getRing(1)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null || ct.getTileType() != TileType.Wheat)
            continue;

          int gc = group_count(TileType.Wheat, cneighbor, world);
          if (gc >= max_gc && gc <= 8) {
            max_cplacable = cplacable;
            max_gc = gc;
          }
        }
      }
    }

    System.out.println("want wheat at: " + max_cplacable);
    controller.placeTile(TileType.Wheat, max_cplacable);
  }

  void place_forest() {
    // beside the forest
    CubeCoordinate max_cplacable = null;
    int max_count = 0; // max here is max
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        if (max_cplacable == null)
          max_cplacable = cplacable;

        int count = 0;
        for (var cneighbor : cplacable.getRing(1)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null || ct.getTileType() != TileType.Forest ||
              ct.getTileType() == TileType.Wheat)
            continue;
          count++;
        }
        if (count >= max_count) {
          max_count = count;
          max_cplacable = cplacable;
        }
      }
    }
    System.out.println("want forest at: " + max_cplacable);
    controller.placeTile(TileType.Forest, max_cplacable);
  }

  void place_doublehouse() {
    // best 3 neighbors
    CubeCoordinate best_cplacable = null;
    int best_count = 0; // best is 3 here
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        if (best_cplacable == null)
          best_cplacable = cplacable;

        int count = 0;
        for (var cneighbor : cplacable.getRing(1)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null || (ct.getTileType() != TileType.DoubleHouse &&
                             ct.getTileType() != TileType.SmallHouse))
            continue;
          count++;
        }
        if (count == 3) {
          best_cplacable = cplacable;
          best_count = 3;
          break;
        } else if (count >= best_count) {
          best_cplacable = cplacable;
          best_count = count;
        }
      }
    }

    System.out.println("want dhouse at: " + best_cplacable);
    if (best_cplacable != null)
      controller.placeTile(TileType.DoubleHouse, best_cplacable);
  }

  void place_smallhouse() {
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        controller.placeTile(TileType.SmallHouse, cplacable);
        return;
      }
    }
  }

  void place_windmill() {
    // see docs
    int best_count_windmills_per_wheat = Integer.MAX_VALUE; // should be lowest
    int best_count_forest = Integer.MAX_VALUE;              // should be lowest
    int best_count_wheat = 0;                               // should be highest
    CubeCoordinate best_cplacable = null;

    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        if (best_cplacable == null)
          best_cplacable = cplacable;

        int count_wheat = 0;
        int count_windmills_per_wheat = 0;
        for (var cneighbor : cplacable.getArea(3)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null || ct.getTileType() != TileType.Wheat)
            continue;
          count_wheat++;

          for (var wheat_neighbor : cneighbor.getArea(3)) {
            var wct = world.getMap().at(wheat_neighbor);
            if (wct == null || wct.getTileType() != TileType.Windmill)
              continue;
            count_windmills_per_wheat++;
          }
        }

        int count_forest = 0;
        for (var cneighbor : cplacable.getRing(1)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null || ct.getTileType() != TileType.Forest)
            continue;
          count_forest++;
        }

        if (count_wheat >= best_count_wheat &&
            count_windmills_per_wheat <= best_count_windmills_per_wheat &&
            count_forest <= best_count_forest) {
          best_count_wheat = count_wheat;
          best_count_windmills_per_wheat = count_windmills_per_wheat;
          best_count_forest = count_forest;

          best_cplacable = cplacable;
        }
      }
    }

    System.out.println("want windmill at: " + best_cplacable);
    if (best_cplacable != null)
      controller.placeTile(TileType.Windmill, best_cplacable);
  }

  void place_beehive() {
    // max wheet or forest in 2 radius
    CubeCoordinate best_cplacable = null;
    int best_count = 0;
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        if (best_cplacable == null)
          best_cplacable = cplacable;

        int count = 0;
        for (var cneighbor : cplacable.getArea(2)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null || (ct.getTileType() != TileType.Forest &&
                             ct.getTileType() != TileType.Wheat))
            continue;
          count++;
        }

        if (count >= best_count) {
          best_count = count;
          best_cplacable = cplacable;
        }
      }
    }

    System.out.println("want beehive at: " + best_cplacable);
    if (best_cplacable != null)
      controller.placeTile(TileType.Beehive, best_cplacable);
  }

  void place_stonequarry() {
    // max level of stone
    CubeCoordinate best_cplacable = null;
    int best_level = 0;
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        if (best_cplacable == null)
          best_cplacable = cplacable;

        int level = 0;
        for (var cneighbor : cplacable.getRing(1)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null)
            continue;

          if (ct.getTileType() == TileType.StoneRocks)
            level = 1;
          else if (ct.getTileType() == TileType.StoneHill)
            level = 2;
          else if (ct.getTileType() == TileType.StoneMountain)
            level = 3;
        }

        if (level >= best_level) {
          best_level = level;
          best_cplacable = cplacable;
        }
      }
    }

    System.out.println("want quarry at: " + best_cplacable);
    if (best_cplacable != null)
      controller.placeTile(TileType.StoneQuarry, best_cplacable);
  }

  void place_moai() {
    // max unique
    CubeCoordinate best_cplacable = null;
    int best_unique = 0;
    int best_houses = 0;
    for (var placed_tile : world.getMap()) {
      for (var cplacable : placed_tile.getCoordinate().getRing(1)) {
        if (!world.getBuildArea().contains(cplacable))
          continue;
        var t = world.getMap().at(cplacable);
        if (t != null)
          continue;

        // TODO: getBuildArea gives entire board fix others!!!
        if (!world.getMap().getNeighbors(cplacable).hasNext())
          continue;

        if (best_cplacable == null)
          best_cplacable = cplacable;

        Set<TileType> unique = new HashSet<>();
        for (var cneighbor : cplacable.getArea(4)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null)
            continue;
          unique.add(ct.getTileType());
        }

        int houses = 0;
        for (var cneighbor : cplacable.getArea(3)) {
          var ct = world.getMap().at(cneighbor);
          if (ct == null)
            continue;
          if (ct.getTileType() == TileType.SmallHouse ||
              ct.getTileType() == TileType.DoubleHouse) {
            houses++;
          }
        }

        if (unique.size() >= best_unique && houses >= best_houses) {
          best_unique = unique.size();
          best_houses = houses;
          best_cplacable = cplacable;
        }
      }
    }

    System.out.println("want moai at: " + best_cplacable);
    if (best_cplacable != null)
      controller.placeTile(TileType.Moai, best_cplacable);
  }

  void place_tile(TileType type, CubeCoordinate coord) {}

  void group_count_set(TileType type, CubeCoordinate start,
                       Set<CubeCoordinate> coords, World world) {

    if (world.getMap().at(start).getTileType() == type) {
      coords.add(world.getMap().at(start).getCoordinate());
    }

    for (var cneighbor : world.getMap().at(start).getCoordinate().getRing(1)) {
      var tneighbor = world.getMap().at(cneighbor);
      if (tneighbor == null)
        continue;

      if (tneighbor.getTileType() == type) {
        if (!coords.contains(tneighbor.getCoordinate())) {
          coords.add(tneighbor.getCoordinate());
          group_count_set(type, cneighbor, coords, world);
        }
      }
    }
  }

  int group_count(TileType type, CubeCoordinate start, World world) {
    Set<CubeCoordinate> coord = new HashSet<CubeCoordinate>();
    group_count_set(type, start, coord, world);

    return coord.size();
  }
}
