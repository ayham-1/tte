import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 *  - the longer a tile is placed the better,
 *  - more tiles placed earlier == better
 *
 *  TODOs:
 *  - implement empty tile counting
 *  - wheat groups should pick to begin new groups on most empty
 *  - stones should pick beside quarries, keeping in mind empty spaces for their
 * quarries
 *  - marketplaces should pick beside most variety
 *  - marketplaces should be configured
 *
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

  abstract class Group {
    MyBot bot = null;

    HashMap<CubeCoordinate, TileType> tiles = new HashMap<>();
    Set<CubeCoordinate> coords_placable = new HashSet<>();
    double last_score = 0.0f;

    Group(MyBot bot) { this.bot = bot; }

    abstract double calc_score();
    abstract double calc_new_score(TileType newtype, CubeCoordinate coord);

    abstract boolean accepts(TileType type);
    abstract boolean fine_neighbor(TileType type);
    abstract void add_tile(TileType type);

    public void add_tile(TileType type, CubeCoordinate coord) {
      this.bot.place_tile(type, coord);
      this.tiles.put(coord, type);

      if (this.coords_placable.contains(coord))
        this.coords_placable.remove(coord);

      for (var cring : coord.getRing(1)) {
        if (world.getMap().at(cring) == null &&
            world.getBuildArea().contains(cring))
          this.coords_placable.add(cring);
      }
    }

    public void update_coords_placable() {
      this.coords_placable = new HashSet<>();
      for (var c : tiles.keySet()) {
        for (var cring : c.getRing(1)) {
          if (this.bot.world.getMap().at(cring) == null &&
              this.bot.world.getBuildArea().contains(cring)) {
            this.coords_placable.add(cring);
          }
        }
      }
    }

    public boolean addable() { return !this.coords_placable.isEmpty(); }
  }

  class WheatGroup extends Group {
    /* Represents a Wheat group, optimal means:
     * - have 9 connected Wheat Tiles
     * - group members all close together
     **/

    WheatGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.Wheat, new_coord);
    }

    @Override
    public double calc_score() {
      int count = this.tiles.size();
      return (count <= 9) ? count : Double.MIN_VALUE;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      if (newtype != TileType.Wheat)
        return Double.MIN_VALUE;

      int count = this.tiles.size() + 1;
      return (count <= 9) ? count : Double.MIN_VALUE;
    }

    @Override
    public void add_tile(TileType type) {
      CubeCoordinate best_cplacable = null;
      int low_avoid = Integer.MAX_VALUE;
      int best_packing = 0;
      for (var cplacable : this.coords_placable) {
        if (best_cplacable == null)
          best_cplacable = cplacable;

        int packing = 0;
        for (var cring : cplacable.getRing(1)) {
          if (this.tiles.containsKey(cring))
            packing++;
        }

        int avoid = this.bot.avoid_coord_for_other_group(TileType.Wheat,
                                                         cplacable, null);

        if (packing >= best_packing && avoid <= low_avoid) {
          best_packing = packing;
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(type, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Wheat;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      if (this.tiles.size() != 9)
        return type == TileType.Beehive || type == TileType.Windmill;
      return type != TileType.Wheat;
    }
  }

  class ForestGroup extends Group {
    /* Represents a forest group, optimal means:
     * - most neighboring forest tiles
     * - group members all close together (implied)
     **/

    ForestGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.Forest, new_coord);
    }

    @Override
    public double calc_score() {
      double score = 0;
      for (var c : this.tiles.keySet()) {
        for (var cring : c.getRing(1)) {
          if (this.tiles.containsKey(cring))
            score++;
        }
      }
      return score;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      double score = this.calc_score();
      for (var cring : coord.getRing(1)) {
        if (this.tiles.containsKey(cring))
          score++;
      }

      return score;
    }

    @Override
    public void add_tile(TileType type) {
      CubeCoordinate best_cplacable = null;
      int best_packing = 0;
      int low_avoid = Integer.MAX_VALUE;
      for (var cplacable : this.coords_placable) {
        if (best_cplacable == null)
          best_cplacable = cplacable;

        int packing = 0;
        for (var cring : cplacable.getRing(1)) {
          if (this.tiles.containsKey(cring))
            packing++;
        }

        int avoid = this.bot.avoid_coord_for_other_group(TileType.Forest,
                                                         cplacable, this);

        if (packing >= best_packing && avoid <= low_avoid) {
          best_packing = packing;
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(type, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Forest;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Beehive;
    }
  }

  class DHouseGroup extends Group {
    /* Represents a double house group, optimal means:
     * - three houses
     **/

    DHouseGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.DoubleHouse, new_coord);

      // add surrounding smalls
      for (var cring : new_coord.getRing(1)) {
        var t = this.bot.world.getMap().at(cring);
        if (t == null)
          continue;

        if (t.getTileType() == TileType.SmallHouse) {
          this.tiles.put(t.getCoordinate(), TileType.SmallHouse);
        }
      }
    }

    @Override
    public double calc_score() {
      int score = this.tiles.size();
      return score;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      double score = this.calc_score() + 1;
      if (score <= 3)
        return score;

      return 0;
    }

    @Override
    public void add_tile(TileType type) {
      CubeCoordinate best_cplacable = null;
      int best_packing = 0;
      for (var cplacable : this.coords_placable) {
        if (best_cplacable == null)
          best_cplacable = cplacable;

        int packing = 0;
        for (var cring : cplacable.getRing(1)) {
          if (this.tiles.containsKey(cring))
            packing++;
        }
        if (packing >= best_packing) {
          best_packing = packing;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(TileType.SmallHouse, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.SmallHouse;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace;
    }
  }

  class SHouseGroup extends Group {
    /* Represents a small house group, optimal means:
     * - three houses
     **/

    SHouseGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.SmallHouse, new_coord);
    }

    @Override
    public double calc_score() {
      int score = this.tiles.size();
      return score;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      return 0;
    }

    @Override
    public void add_tile(TileType type) {
      CubeCoordinate best_cplacable = null;
      int low_avoid = Integer.MAX_VALUE;
      for (var cplacable : this.coords_placable) {
        if (best_cplacable == null)
          best_cplacable = cplacable;

        int avoid = this.bot.avoid_coord_for_other_group(TileType.SmallHouse,
                                                         cplacable, null);
        if (avoid <= low_avoid) {
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(TileType.SmallHouse, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.SmallHouse;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.DoubleHouse;
    }
  }

  class StoneRocksGroup extends Group {
    /* Represents a stone rocks group, optimal means:
     * - beside one quarry
     **/

    StoneRocksGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.StoneRocks, new_coord);
    }

    @Override
    public double calc_score() {
      int score = this.tiles.size();
      return score;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      return Math.min(this.calc_score() + 1.0f, 2.0f);
    }

    @Override
    public boolean addable() {
      return this.calc_score() + 1.0f < 3.0f && super.addable();
    }

    @Override
    public void add_tile(TileType type) {
      int low_avoid = Integer.MAX_VALUE;
      CubeCoordinate best_cplacable = null;
      for (var cplacable : this.coords_placable) {
        int avoid = this.bot.avoid_coord_for_other_group(type, cplacable, this);
        if (avoid <= low_avoid) {
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(type, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.StoneQuarry;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type != TileType.StoneQuarry;
    }
  }

  class StoneHillGroup extends Group {
    /* Represents a stone hill group, optimal means:
     * - beside two quarries
     **/

    StoneHillGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.StoneHill, new_coord);
    }

    @Override
    public double calc_score() {
      int score = this.tiles.size();
      return score;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      return Math.min(this.calc_score() + 1.0f, 3.0f);
    }

    @Override
    public boolean addable() {
      return this.calc_score() + 1.0f < 3.0f && super.addable();
    }

    @Override
    public void add_tile(TileType type) {
      int low_avoid = Integer.MAX_VALUE;
      CubeCoordinate best_cplacable = null;
      for (var cplacable : this.coords_placable) {
        int avoid = this.bot.avoid_coord_for_other_group(type, cplacable, this);
        if (avoid <= low_avoid) {
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(type, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.StoneQuarry;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type != TileType.StoneQuarry;
    }
  }

  class StoneMountainGroup extends Group {
    /* Represents a stone mountain group, optimal means:
     * - beside three quarries
     **/

    StoneMountainGroup(MyBot bot, CubeCoordinate new_coord) {
      super(bot);
      this.add_tile(TileType.StoneMountain, new_coord);
    }

    @Override
    public double calc_score() {
      int score = this.tiles.size();
      return score;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      return Math.min(this.calc_score() + 1.0f, 4.0f);
    }

    @Override
    public boolean addable() {
      return this.calc_score() + 1.0f < 4.0f && super.addable();
    }

    @Override
    public void add_tile(TileType type) {
      int low_avoid = Integer.MAX_VALUE;
      CubeCoordinate best_cplacable = null;
      for (var cplacable : this.coords_placable) {
        int avoid = this.bot.avoid_coord_for_other_group(type, cplacable, this);
        if (avoid <= low_avoid) {
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      this.add_tile(type, best_cplacable);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.StoneQuarry;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type != TileType.StoneQuarry;
    }
  }

  // API vars
  World world;
  Controller controller;

  //// state vars
  int round = 0;
  boolean is_first = true;
  boolean must_win = false;
  boolean redrawn = false;

  /* stores all groups that are tracked on the map,
   * there is no garantue that all tiles are in a group,
   * or that each tile appears in a single group.
   * */
  ArrayList<Group> groups = new ArrayList<>();

  /* placable coords that are not-isolated, manually updated every placed
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

    if (this.round != world.getRound()) {
      this.round = world.getRound();
      if (!this.reachable_money || !this.reachable_food ||
          !this.reachable_materials)
        this.must_win = true;
      else {
        this.must_win = false;
        this.redrawn = false;
      }
      this.update_coords_placable();
    }

    this.update_resources_stat();

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

    // this.setup_marketplaces();
    if (!this.controller.actionPossible())
      return;

    if (world.getHand().isEmpty() && world.getRedrawTime() > 5) {
      // determine if we need to redraw since we are not hitting the target
      // resources
      var cost = world.getRedrawCosts();
      boolean redrawable = this.resource_current.money >= cost.money &&
                           this.resource_current.food >= cost.food &&
                           this.resource_current.materials >= cost.materials;
      if (redrawable) {
        if ((!this.reachable_money || !this.reachable_food ||
             !this.reachable_materials) &&
            this.must_win) {
          // controller.redraw();
          this.redrawn = true;
        } else {
          double money = this.resource_current.money +
                         this.resource_growth.money * this.round_time_left;

          double food = this.resource_current.food +
                        this.resource_growth.food * this.round_time_left;

          double mat = this.resource_current.materials +
                       this.resource_growth.materials * this.round_time_left;

          double growth_expectancy = 0.15f;
          double offset = (growth_expectancy / this.round);

          if (money - cost.money >=
                  this.resource_target.money * (1.0f - offset) &&
              food - cost.food >= this.resource_target.food * (1.0f - offset) &&
              mat - cost.materials >=
                  this.resource_target.materials * (1.0f - offset))
            if (this.coords_placable.size() >= 5) // don't have extra on hand
              controller.redraw();
        }
      }
    }

    for (var card : world.getHand()) {
      if (!controller.actionPossible())
        return;

      if (is_first) {
        // this.place_tile(card, new CubeCoordinate());
        this.coords_placable.add(new CubeCoordinate());
        is_first = false;
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
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int avoid = this.avoid_coord_for_other_group(TileType.Marketplace,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    this.place_tile(TileType.Marketplace, best_cplacable);
  }

  void place_grass() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.Grass, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    this.place_tile(TileType.Grass, best_cplacable);
  }

  void place_stonehill() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.StoneHill, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      StoneHillGroup new_group = new StoneHillGroup(this, best_cplacable);
      this.groups.add(new_group);
    }
  }

  void place_stonemountain() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid = this.avoid_coord_for_other_group(TileType.StoneMountain,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      StoneMountainGroup new_group =
          new StoneMountainGroup(this, best_cplacable);
      this.groups.add(new_group);
    }
  }

  void place_stonerocks() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid = this.avoid_coord_for_other_group(TileType.StoneRocks,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      StoneRocksGroup new_group = new StoneRocksGroup(this, best_cplacable);
      this.groups.add(new_group);
    }
  }

  void place_wheat() {
    // search for a group that accepts wheat
    double max_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.Wheat) && group.addable()) {
        double score = group.calc_new_score(
            TileType.Wheat, group.coords_placable.iterator().next());
        if (score >= max_score) {
          max_score = score;
          max_group = group;
        }
      }
    }

    // refuse groups larger than 8
    if (max_group != null && max_group.tiles.size() <= 8) {
      max_group.add_tile(TileType.Wheat);
      return;
    }

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.Wheat, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      WheatGroup new_group = new WheatGroup(this, best_cplacable);
      this.groups.add(new_group);
    }
  }

  void place_forest() {
    // beside the forest
    double max_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.Forest) && group.addable()) {
        double score = group.calc_new_score(
            TileType.Forest, group.coords_placable.iterator().next());

        if (score >= max_score) {
          max_score = score;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      max_group.add_tile(TileType.Forest);
      return;
    }

    int low_avoid = Integer.MAX_VALUE;
    CubeCoordinate best_cplacable = null;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.Forest, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      ForestGroup new_group = new ForestGroup(this, best_cplacable);
      this.groups.add(new_group);
    }
  }

  void place_doublehouse() {
    // best 3 neighbors, and neighbors don't have more than 2
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int best_count = 0; // best is 3 here
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int count = 0;
      boolean use = true;
      for (var cneighbor : cplacable.getRing(1)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null || (ct.getTileType() != TileType.DoubleHouse &&
                           ct.getTileType() != TileType.SmallHouse))
          continue;
        count++;

        if (ct.getTileType() == TileType.DoubleHouse) {
          int houses = 0;
          for (var cring : cneighbor.getRing(1)) {
            var t = world.getMap().at(cring);
            if (t == null)
              continue;
            if (t.getTileType() == TileType.SmallHouse ||
                t.getTileType() == TileType.DoubleHouse)
              houses++;
          }
          if (houses >= 3) {
            use = false;
            break;
          }
        }
        if (!use)
          continue;
      }
      if (!use)
        continue;

      int avoid = this.avoid_coord_for_other_group(TileType.DoubleHouse,
                                                   cplacable, null);

      if (avoid <= low_avoid)
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
    Group max_group = null;
    double max_score = 0;
    for (var group : this.groups) {
      if (group.accepts(TileType.SmallHouse) && group.addable()) {
        double score = group.calc_new_score(
            TileType.SmallHouse, group.coords_placable.iterator().next());
        if (score >= max_score) {
          max_score = score;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      max_group.add_tile(TileType.SmallHouse);
      return;
    }

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid = this.avoid_coord_for_other_group(TileType.SmallHouse,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      SHouseGroup new_group = new SHouseGroup(this, best_cplacable);
      this.groups.add(new_group);
    }
  }

  void place_windmill() {
    // see docs
    int best_count_windmills_per_wheat = Integer.MAX_VALUE; // should be lowest
    int best_count_forest = Integer.MAX_VALUE;              // should be lowest
    int best_count_wheat = 0;                               // should be highest
    int low_avoid = Integer.MAX_VALUE;
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

      int avoid =
          this.avoid_coord_for_other_group(TileType.Windmill, cplacable, null);

      if (count_wheat >= best_count_wheat &&
          count_windmills_per_wheat <= best_count_windmills_per_wheat &&
          count_forest <= best_count_forest && avoid <= low_avoid) {
        best_count_wheat = count_wheat;
        best_count_windmills_per_wheat = count_windmills_per_wheat;
        best_count_forest = count_forest;
        low_avoid = avoid;

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
    double max_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.StoneQuarry) && group.addable()) {
        double score = group.calc_new_score(
            TileType.StoneQuarry, group.coords_placable.iterator().next());

        if (score >= max_score) {
          max_score = score;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      max_group.add_tile(TileType.StoneQuarry);
      return;
    }

    int low_avoid = Integer.MAX_VALUE;
    CubeCoordinate best_cplacable = null;
    for (var cplacable : this.coords_placable) {
      int avoid = this.avoid_coord_for_other_group(TileType.StoneQuarry,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
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

  static HashMap<CubeCoordinate, Boolean> selling = new HashMap<>();
  void setup_marketplaces() {
    for (var t : world.getMap()) {
      if (!this.controller.actionPossible())
        return;

      if (t.getTileType() != TileType.Marketplace)
        continue;

      if (!this.reachable_money && (!selling.containsKey(t.getCoordinate()) ||
                                    !selling.get(t.getCoordinate()))) {
        controller.configureMarket(t.getCoordinate(),
                                   this.reachable_food ? 1.0f : 0.0f,
                                   this.reachable_materials ? 1.0f : 0.0f);
        selling.put(t.getCoordinate(), true);
      }
    }
  }

  void place_tile(TileType type, CubeCoordinate coord) {
    System.out.println("want " + type + " on " + coord);
    if (coord == null)
      return;
    System.out.println("is_coord_usable: " +
                       (world.getMap().at(coord) == null));

    controller.placeTile(type, coord);

    if (this.coords_placable.contains(coord))
      this.coords_placable.remove(coord);

    for (var cring : coord.getRing(1)) {
      if (world.getMap().at(cring) == null &&
          world.getBuildArea().contains(cring))
        this.coords_placable.add(cring);
    }

    for (var group : this.groups)
      group.update_coords_placable();

    // this.coords_placable.removeIf(t -> !world.getBuildArea().contains(t));
  }

  int avoid_coord_for_other_group(TileType type, CubeCoordinate coord,
                                  Group current) {
    int should_avoid = 0;

    for (var group : this.groups) {
      if (group == current)
        continue;

      if (!group.coords_placable.contains(coord))
        continue;

      if (!group.fine_neighbor(type))
        should_avoid++;
    }

    return should_avoid;
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
