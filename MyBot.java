import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import page.codeberg.terratactician_expandoria.*;
import page.codeberg.terratactician_expandoria.bots.*;
import page.codeberg.terratactician_expandoria.world.CubeCoordinate;
import page.codeberg.terratactician_expandoria.world.Metrics;
import page.codeberg.terratactician_expandoria.world.tiles.*;
import page.codeberg.terratactician_expandoria.world.tiles.Tile.TileType;

/*
 * Observations:
 *  - only one placement of a tile is allowed per turn
 *  - memory is more abundant than time!
 *
 * TODOs:
 * - [x] separate the different tile type placements into functions.
 * - [x] track the usable non isolated tiles ourselves, so that iteration is
 * fast
 * - [ ] track the groups of different tiles together ourselves
 * - [ ] find a way to integrate the needs of the tracked groups surrounding the
 * considered usable tile when picking which usable tile to use
 * - [ ] investigate more abstract considerations for groups:
 *    - [ ] prefer packed groups
 *    - [ ] prefer patterns of groups
 * */

public class MyBot extends ChallengeBot {
  static final boolean PRINT_DEBUG = false;

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

  // API vars
  World world;
  Controller controller;

  //// state vars
  int round = 0;
  boolean is_first = true;

  /* placable coords that are not-isolated and manually updated every placed
   * tile and every round change */
  Set<CubeCoordinate> coords_placable = new HashSet<CubeCoordinate>();

  Metrics resource_current = new Metrics();
  Metrics resource_target = new Metrics();
  Metrics resource_growth = new Metrics();
  double round_time_left = 0.0f;

  boolean reachable_money = false;
  boolean reachable_food = false;
  boolean reachable_materials = false;

  double resource_perc_money = 0;
  double resource_perc_food = 0;
  double resource_perc_materials = 0;

  @Override
  public void executeTurn(World world, Controller controller) {
    this.world = world;
    this.controller = controller;
    this.update_resources_stat();

    if (this.round != world.getRound()) {
      this.round = world.getRound();
      this.update_coords_placable();
    }

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
      boolean redrawable = this.resource_current.money >= cost.money &&
                           this.resource_current.food >= cost.food &&
                           this.resource_current.materials >= cost.materials;
      if (redrawable)
        if ((!this.reachable_money || !this.reachable_food ||
             !this.reachable_materials) &&
            this.resource_perc_money > 0.75 && this.resource_perc_food > 0.75 &&
            this.resource_perc_materials > 0.75)
          controller.redraw();
    }

    for (var card : world.getHand()) {
      if (!controller.actionPossible())
        return;

      if (is_first) {
        this.place_tile(card, new CubeCoordinate());
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
    for (var cplacable : this.coords_placable) {
      this.place_tile(TileType.Marketplace, cplacable);
      return;
    }
  }

  void place_grass() {
    for (var cplacable : this.coords_placable) {
      this.place_tile(TileType.Grass, cplacable);
      return;
    }
  }

  void place_stonehill() {
    for (var cplacable : this.coords_placable) {
      this.place_tile(TileType.StoneHill, cplacable);
      return;
    }
  }

  void place_stonemountain() {
    for (var cplacable : this.coords_placable) {
      this.place_tile(TileType.StoneMountain, cplacable);
      return;
    }
  }

  void place_stonerocks() {
    for (var cplacable : this.coords_placable) {
      this.place_tile(TileType.StoneRocks, cplacable);
      return;
    }
  }

  void place_wheat() {
    // beside other wheats
    CubeCoordinate max_cplacable = null;
    int max_gc = 0; // max is less than 8 here
    ArrayList<CubeCoordinate> placable_reversed = new ArrayList<>();
    for (var t : this.coords_placable)
      placable_reversed.add(t);
    Collections.reverse(placable_reversed);

    for (var cplacable : placable_reversed) {
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

    if (max_cplacable != null)
      this.place_tile(TileType.Wheat, max_cplacable);
  }

  void place_forest() {
    // beside the forest
    CubeCoordinate max_cplacable = null;
    int max_count = 0; // max here is max
    for (var cplacable : this.coords_placable) {
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
    if (max_cplacable != null)
      this.place_tile(TileType.Forest, max_cplacable);
  }

  void place_doublehouse() {
    // best 3 neighbors
    CubeCoordinate best_cplacable = null;
    int best_count = 0; // best is 3 here
    for (var cplacable : this.coords_placable) {
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

    if (best_cplacable != null)
      this.place_tile(TileType.DoubleHouse, best_cplacable);
  }

  void place_smallhouse() {
    for (var cplacable : this.coords_placable) {
      this.place_tile(TileType.SmallHouse, cplacable);
      return;
    }
  }

  void place_windmill() {
    // see docs
    int best_count_windmills_per_wheat = Integer.MAX_VALUE; // should be lowest
    int best_count_forest = Integer.MAX_VALUE;              // should be lowest
    int best_count_wheat = 0;                               // should be highest
    CubeCoordinate best_cplacable = null;

    for (var cplacable : this.coords_placable) {
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

    if (best_cplacable != null)
      this.place_tile(TileType.Windmill, best_cplacable);
  }

  void place_beehive() {
    // max wheet or forest in 2 radius
    CubeCoordinate best_cplacable = null;
    int best_count = 0;
    for (var cplacable : this.coords_placable) {
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

    if (best_cplacable != null)
      this.place_tile(TileType.Beehive, best_cplacable);
  }

  void place_stonequarry() {
    // max level of stone
    CubeCoordinate best_cplacable = null;
    int best_level = 0;
    for (var cplacable : this.coords_placable) {
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

    if (best_cplacable != null)
      this.place_tile(TileType.StoneQuarry, best_cplacable);
  }

  void place_moai() {
    // max unique
    CubeCoordinate best_cplacable = null;
    int best_unique = 0;
    int best_houses = 0;
    for (var cplacable : this.coords_placable) {
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

    if (best_cplacable != null)
      this.place_tile(TileType.Moai, best_cplacable);
  }

  void place_tile(TileType type, CubeCoordinate coord) {
    System.out.println("want " + type + " at " + coord);
    controller.placeTile(type, coord);

    if (this.coords_placable.contains(coord))
      this.coords_placable.remove(coord);

    for (var cring : coord.getRing(1)) {
      if (world.getMap().at(cring) == null &&
          world.getBuildArea().contains(cring))
        this.coords_placable.add(cring);
    }

    // this.coords_placable.removeIf(t -> !world.getBuildArea().contains(t));
  }

  void update_coords_placable() {
    for (var c : world.getBuildArea()) {
      if (world.getMap().at(c) != null)
        continue;
      if (world.getMap().getNeighbors(c).hasNext() == true)
        this.coords_placable.add(c);
    }
  }

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

  void update_resources_stat() {
    this.resource_current = world.getResources();
    this.resource_target = world.getTargetResources();
    this.resource_growth = world.getResourcesRate();
    this.round_time_left = world.getRoundTime();

    this.reachable_money =
        (this.resource_current.money +
         (this.resource_growth.money * this.round_time_left)) >=
        this.resource_target.money;

    this.reachable_food =
        (this.resource_current.food +
         (this.resource_growth.food * this.round_time_left)) >=
        this.resource_target.food;

    this.reachable_materials =
        (this.resource_current.materials +
         (this.resource_growth.materials * this.round_time_left)) >=
        this.resource_target.materials;

    this.resource_perc_money =
        this.resource_current.money / this.resource_target.money;
    this.resource_perc_food =
        this.resource_current.food / this.resource_target.food;
    this.resource_perc_materials =
        this.resource_current.materials / this.resource_target.materials;

    if (PRINT_DEBUG) {
      System.out.println("[current] money=" + this.resource_current.money +
                         " food=" + this.resource_current.food +
                         " materials=" + this.resource_current.materials);

      System.out.println("[target] money=" + this.resource_target.money +
                         " food=" + this.resource_target.food +
                         " materials=" + this.resource_target.materials);

      System.out.println("[growth] money=" + this.resource_growth.money +
                         " food=" + this.resource_growth.food +
                         " materials=" + this.resource_growth.materials);

      System.out.println(
          "[perc] money=" + String.format("%.3f", this.resource_perc_money) +
          " food=" + String.format("%.3f", this.resource_perc_food) +
          " materials=" +
          String.format("%.3f", this.resource_perc_materials));
    }
  }
}
