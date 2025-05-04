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
 *  - marketplaces convert food/material to money, and are actually very
 * important mid-game
 *
 *  TODOs:
 *  - Wheats are not being added to wheat groups (out of build area attempt
 * suspected)
 *  - marketplaces MUST have houses
 *  - force marketplaces to be placed beside generators of resources we can sell
 *  - quarries should place themselves when no available stones with empty
 * possibility
 *  - stones should pick beside quarries, keeping in mind empty spaces for their
 * quarries
 *  - give more value for higher resources growth available when placing
 * marketplaces
 *  - solve housing crisis
 *  - find a better marketplace calculation
 *
 *  - maoi effect don't stack
 *  - new forest (also groups) should prefer to circle beehives
 *
 *  - find a way to order the placement of cards in hands
 *  - find optimal new wheat group, before deciding whether to make a new one
 *
 *  - avoid beehives closing forests too much
 *
 *
 *  - investigate sectors
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

    public void add_tile(TileType type, CubeCoordinate coord) throws Exception {
      if (!this.bot.place_tile(type, coord))
        throw new Exception("place_tile() failed");

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

    WheatGroup(MyBot bot, CubeCoordinate new_coord) throws Exception {
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
        boolean has_foreign = false;
        int packing = 0;
        for (var cring : cplacable.getRing(1)) {
          var t = this.bot.world.getMap().at(cring);
          if (this.tiles.containsKey(cring))
            packing++;
          else if (t != null && t.getTileType() == TileType.Wheat) {
            has_foreign = true;
            break;
          }
        }

        // avoid joining with other groups
        if (has_foreign)
          continue;

        int avoid = this.bot.avoid_coord_for_other_group(TileType.Wheat,
                                                         cplacable, null);

        if (packing >= best_packing && avoid <= low_avoid) {
          best_packing = packing;
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      try {
        this.add_tile(type, best_cplacable);
      } catch (Exception e) {
      }
    }

    @Override
    public boolean addable() {
      boolean o = super.addable();

      // refuse making groups larger than 8
      if (this.tiles.size() >= 9)
        return false;

      for (var cplacable : this.coords_placable) {
        boolean could_be_placable = true;
        for (var cring : cplacable.getRing(1)) {
          var t = this.bot.world.getMap().at(cring);
          if (t == null || this.tiles.containsKey(cring))
            continue;

          if (t.getTileType() == TileType.Wheat) {
            could_be_placable = false;
            break;
          }
        }

        if (could_be_placable)
          return o;
      }

      return false;
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Wheat;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Beehive || type == TileType.Windmill ||
          type == TileType.Marketplace;
    }
  }

  class ForestGroup extends Group {
    /* Represents a forest group, optimal means:
     * - most neighboring forest tiles
     * - group members all close together (implied)
     **/

    ForestGroup(MyBot bot, CubeCoordinate new_coord) throws Exception {
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
      try {
        this.add_tile(type, best_cplacable);
      } catch (Exception e) {
      }
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Forest;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Beehive || type == TileType.Marketplace;
    }
  }

  class HousingGroup extends Group {
    /* Represents a housing group, optimal means:
     * - double houses have exactly three neighbors
     * - three small houses
     **/

    HousingGroup(MyBot bot, TileType type, CubeCoordinate new_coord)
        throws Exception {
      super(bot);
      this.add_tile(type, new_coord);
    }

    @Override
    public double calc_score() {
      double sscore = 0;
      double dscore = 0;

      for (var t : this.tiles.entrySet()) {
        if (t.getValue() == TileType.SmallHouse)
          sscore++;
        else if (t.getValue() == TileType.DoubleHouse) {
          int count = 0;
          for (var cring : t.getKey().getRing(1)) {
            if (this.tiles.containsKey(cring))
              count++;
          }
          dscore += count / 3; //(count == 3) ? 1 : 0;
        }
      }

      return sscore + dscore;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      // add coord to tiles temp.
      this.tiles.put(coord, newtype);

      double score = this.calc_score();

      this.tiles.remove(coord);

      return score;
    }

    @Override
    public void add_tile(TileType type) {
      if (type == TileType.SmallHouse) {
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

          int avoid = this.bot.avoid_coord_for_other_group(TileType.SmallHouse,
                                                           cplacable, this);

          if (packing >= best_packing && avoid <= low_avoid) {
            best_packing = packing;
            low_avoid = avoid;
            best_cplacable = cplacable;
          }
        }
        try {
          this.add_tile(TileType.SmallHouse, best_cplacable);
        } catch (Exception e) {
        }
      } else if (type == TileType.DoubleHouse) {
        CubeCoordinate best_cplacable = null;
        int low_avoid = Integer.MAX_VALUE;
        int best_count = 0;
        for (var cplacable : this.coords_placable) {
          if (best_cplacable == null)
            best_cplacable = cplacable;

          int count = 0;
          boolean use = true;
          for (var cring : cplacable.getRing(1)) {
            var t = this.bot.world.getMap().at(cring);
            if (this.tiles.containsKey(cring) &&
                this.tiles.get(cring) == TileType.SmallHouse)
              count++;
            else if (t != null) {
              if (t.getTileType() == TileType.SmallHouse ||
                  t.getTileType() == TileType.DoubleHouse)
                use = false;
              break;
            }
          }
          if (!use)
            continue;

          int avoid = this.bot.avoid_coord_for_other_group(TileType.DoubleHouse,
                                                           cplacable, this);

          if (avoid <= low_avoid && best_count <= 3 && count >= best_count) {
            low_avoid = avoid;
            best_count = count;
            best_cplacable = cplacable;
          }
        }
        try {
          this.add_tile(TileType.DoubleHouse, best_cplacable);
        } catch (Exception e) {
        }
      }
    }

    @Override
    public boolean accepts(TileType type) {
      double sscore = 0;
      double dscore = 0;
      int dcount = 0;

      for (var t : this.tiles.entrySet()) {
        if (t.getValue() == TileType.SmallHouse)
          sscore++;
        else if (t.getValue() == TileType.DoubleHouse) {
          int count = 0;
          for (var cring : t.getKey().getRing(1)) {
            if (this.tiles.containsKey(cring))
              count++;
          }
          dscore += (count == 3) ? 1 : 0;
          dcount++;
        }
      }
      if (type == TileType.SmallHouse && sscore < 3)
        return true;
      if (type == TileType.DoubleHouse && dscore <= 1)
        return true;
      return false;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace;
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
  Set<CubeCoordinate> coords_placable = new HashSet<>();
  HashMap<CubeCoordinate, Boolean> marketplaces = new HashMap<>();

  Metrics resource_current = new Metrics();
  Metrics resource_target = new Metrics();
  Metrics resource_growth = new Metrics();

  // per entire hand delta growth change
  Metrics resource_growth_delta = new Metrics();
  Metrics resource_growth_last = new Metrics();
  int resource_delta_count = 0;
  boolean growth_last_updated = false;

  double round_time_left = 0.0f;

  boolean reachable_money = false;
  boolean reachable_food = false;
  boolean reachable_materials = false;

  double resource_perc_money = 0;
  double resource_perc_food = 0;
  double resource_perc_materials = 0;

  boolean event_empty_hand_start = false;

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

    if (!this.world.getHand().isEmpty()) {
      this.resource_growth_last = this.resource_growth;
      growth_last_updated = true;
    } else if (this.world.getHand().isEmpty() && growth_last_updated) {
      this.resource_growth_delta.money +=
          this.resource_growth.money - this.resource_growth_last.money;

      this.resource_growth_delta.food +=
          this.resource_growth.food - this.resource_growth_last.food;

      this.resource_growth_delta.materials +=
          this.resource_growth.materials - this.resource_growth_last.materials;
      this.resource_delta_count++;
      growth_last_updated = false;
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

    if (this.world.getHand().isEmpty()) {
      for (var market : this.marketplaces.keySet())
        this.marketplaces.put(market, false);
    }

    this.setup_marketplaces();
    if (!this.controller.actionPossible())
      return;

    if (world.getHand().isEmpty()) {
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
          this.redrawn = true;
        } else {
          double money = this.resource_current.money +
                         this.resource_growth.money * this.round_time_left;

          double food = this.resource_current.food +
                        this.resource_growth.food * this.round_time_left;

          double mat = this.resource_current.materials +
                       this.resource_growth.materials * this.round_time_left;

          double offset_m =
              (this.resource_growth_delta.money / this.resource_delta_count) *
              this.round_time_left;

          double offset_f =
              (this.resource_growth_delta.food / this.resource_delta_count) *
              this.round_time_left;

          double offset_mat = (this.resource_growth_delta.materials /
                               this.resource_delta_count) *
                              this.round_time_left;

          double early_bias = 1 - (Math.pow(Math.E, -(this.round * 2.5)));

          if ((money - cost.money) + offset_m >
                  this.resource_target.money * early_bias &&
              (food - cost.food) + offset_f >
                  this.resource_target.food * early_bias &&
              (mat - cost.materials) + offset_mat >
                  this.resource_target.materials * early_bias)
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
    boolean prefers_food = this.market_prefers_food();

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int high_houses = 0;
    int high_resources = 0;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int houses = 0;
      int resources = 0;
      for (var cring : cplacable.getRing(3)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.SmallHouse ||
            t.getTileType() == TileType.DoubleHouse)
          houses++;
        else if (t.getTileType() == TileType.Wheat ||
                 t.getTileType() == TileType.Beehive)
          resources += (prefers_food) ? 1 : 1; // materials is just op
        else if (t.getTileType() == TileType.Forest ||
                 t.getTileType() == TileType.StoneQuarry)
          resources += (prefers_food) ? 2 : 1;
      }

      // no use of having no houses
      if (houses == 0)
        continue;

      int avoid = this.avoid_coord_for_other_group(TileType.Marketplace,
                                                   cplacable, null);
      if (avoid <= low_avoid && houses >= high_houses &&
          resources >= high_resources) {
        low_avoid = avoid;
        high_houses = houses;
        high_resources = resources;
        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null) {
      if (this.place_tile(TileType.Marketplace, best_cplacable))
        this.marketplaces.put(best_cplacable, false);
    }
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
      this.place_tile(TileType.StoneHill, best_cplacable);
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
      this.place_tile(TileType.StoneMountain, best_cplacable);
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
      this.place_tile(TileType.StoneRocks, best_cplacable);
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

    if (max_group != null) {
      max_group.add_tile(TileType.Wheat);
      return;
    }

    // new wheat group
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int max_windmill = 0;
    int max_beehive = 0;
    for (var cplacable : this.coords_placable) {
      boolean wheat_neighbors = false;

      int windmill = 0;
      int beehive = 0;
      for (var cring : cplacable.getRing(1)) {
        var t = world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.Windmill)
          windmill++;
        else if (t.getTileType() == TileType.Beehive)
          beehive++;
        else if (t.getTileType() == TileType.Wheat) {
          wheat_neighbors = true;
          break;
        }
      }

      // refuse to start new wheat group beside wheats
      if (wheat_neighbors)
        continue;

      int avoid =
          this.avoid_coord_for_other_group(TileType.Wheat, cplacable, null);
      if (avoid <= low_avoid && windmill >= max_windmill &&
          beehive >= max_beehive) {
        low_avoid = avoid;
        max_windmill = windmill;
        max_beehive = beehive;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      try {
        WheatGroup new_group = new WheatGroup(this, best_cplacable);
        this.groups.add(new_group);
      } catch (Exception e) {
      }
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
      try {
        max_group.add_tile(TileType.Forest);
      } catch (Exception e) {
      }
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
      try {
        ForestGroup new_group = new ForestGroup(this, best_cplacable);
        this.groups.add(new_group);
      } catch (Exception e) {
      }
    }
  }

  void place_doublehouse() {
    double max_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.DoubleHouse) && group.addable()) {
        double score = group.calc_new_score(
            TileType.DoubleHouse, group.coords_placable.iterator().next());

        if (score >= max_score) {
          max_score = score;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      max_group.add_tile(TileType.DoubleHouse);
      return;
    }

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int high_markets = 0;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int markets = 0;
      for (var cring : cplacable.getArea(3)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.Marketplace)
          markets++;
      }

      int avoid = this.avoid_coord_for_other_group(TileType.DoubleHouse,
                                                   cplacable, null);

      if (avoid <= low_avoid && markets >= high_markets) {
        best_cplacable = cplacable;
        low_avoid = avoid;
        high_markets = markets;
      }
    }

    if (best_cplacable != null) {
      try {
        HousingGroup hgroup =
            new HousingGroup(this, TileType.DoubleHouse, best_cplacable);
        this.groups.add(hgroup);
      } catch (Exception e) {
      }
    }
  }

  void place_smallhouse() {
    double max_score = 0.0f;
    Group max_group = null;
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
    int low_count = 0;
    int high_markets = 0;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int count = 0;
      int markets = 0;
      for (var cring : cplacable.getRing(1)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.SmallHouse)
          count++;
        if (t.getTileType() == TileType.Marketplace)
          markets++;
      }

      int avoid = this.avoid_coord_for_other_group(TileType.SmallHouse,
                                                   cplacable, null);

      if (avoid <= low_avoid && low_count >= count && markets >= high_markets) {
        best_cplacable = cplacable;
        low_avoid = avoid;
        low_count = count;
        high_markets = markets;
      }
    }

    if (best_cplacable != null) {
      try {
        HousingGroup hgroup =
            new HousingGroup(this, TileType.SmallHouse, best_cplacable);
        this.groups.add(hgroup);
      } catch (Exception e) {
      }
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
      if (count_wheat == 0)
        continue;

      int count_forest = 0;
      for (var cneighbor : cplacable.getRing(1)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null || ct.getTileType() != TileType.Forest)
          continue;
        count_forest++;
        break;
      }
      if (count_forest > 0)
        continue;

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
    int low_avoid = Integer.MAX_VALUE;
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

      int avoid =
          this.avoid_coord_for_other_group(TileType.Beehive, cplacable, null);

      if (count >= best_count && avoid <= low_avoid) {
        best_count = count;
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null)
      this.place_tile(TileType.Beehive, best_cplacable);
  }

  void place_stonequarry() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int avoid = this.avoid_coord_for_other_group(TileType.StoneQuarry,
                                                   cplacable, null);

      boolean usuable = false;
      for (var cring : cplacable.getRing(1)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;

        if (t.getTileType() != TileType.StoneHill &&
            t.getTileType() != TileType.StoneRocks &&
            t.getTileType() != TileType.StoneMountain)
          continue;

        int count_using_quarries = 0;
        for (var cquarry : cring.getRing(1)) {
          var tquarry = this.world.getMap().at(cquarry);
          if (tquarry == null)
            continue;
          if (tquarry.getTileType() == TileType.StoneQuarry)
            count_using_quarries++;
        }

        if (t.getTileType() == TileType.StoneMountain &&
            count_using_quarries <= 2)
          usuable = true;
        else if (t.getTileType() == TileType.StoneHill &&
                 count_using_quarries <= 1)
          usuable = true;
        else if (t.getTileType() == TileType.StoneRocks &&
                 count_using_quarries == 0)
          usuable = true;
        if (usuable)
          break;
      }

      if (!usuable)
        continue;

      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null)
      this.place_tile(TileType.StoneQuarry, best_cplacable);
  }

  void place_moai() {
    // see docs
    int best_unique = 0;                    // should be highest
    int low_count_maoi = Integer.MAX_VALUE; // should be lowest
    int high_houses = 0;
    int low_avoid = Integer.MAX_VALUE;
    CubeCoordinate best_cplacable = null;

    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int count_maoi_per_house = 0;
      for (var cring : cplacable.getRing(6)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t == null || t.getTileType() != TileType.Moai)
          continue;

        count_maoi_per_house++;
      }

      Set<TileType> unique = new HashSet<>();
      for (var cneighbor : cplacable.getArea(4)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null)
          continue;
        unique.add(ct.getTileType());
      }

      int count = 0;
      for (var cring : cplacable.getRing(3)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() != TileType.SmallHouse ||
            t.getTileType() != TileType.DoubleHouse)
          continue;

        count++;
      }

      int avoid =
          this.avoid_coord_for_other_group(TileType.Moai, cplacable, null);

      if (unique.size() >= best_unique &&
          count_maoi_per_house <= low_count_maoi && count >= high_houses &&
          avoid <= low_avoid) {
        low_count_maoi = count_maoi_per_house;
        best_unique = unique.size();
        low_avoid = avoid;
        high_houses = count;

        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null)
      this.place_tile(TileType.Moai, best_cplacable);
  }

  boolean place_tile(TileType type, CubeCoordinate coord) {
    System.out.println("want " + type + " on " + coord);
    if (coord == null)
      return false;
    System.out.println("is_coord_usable: " +
                       (world.getMap().at(coord) == null));
    if (world.getMap().at(coord) != null)
      return false;

    if (!world.getBuildArea().contains(coord))
      return false;

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
    return true;
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

  boolean market_prefers_food() {
    double perc_food = Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                                   this.resource_growth.food)),
                                1);
    double perc_mat =
        Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                    this.resource_growth.materials)),
                 1);
    return perc_food >= perc_mat;
  }

  void setup_marketplaces() {
    for (var market : this.marketplaces.entrySet()) {
      if (market.getValue() == false) {
        // double perc_food =
        //     Math.min((this.resource_growth.food - this.resource_growth.money)
        //     /
        //                  this.resource_growth.money,
        //              1);
        // double perc_mat = Math.min(
        //     (this.resource_growth.materials - this.resource_growth.money) /
        //         this.resource_growth.money,
        //     1);
        double perc_food =
            (this.resource_growth.money < this.resource_growth.food)
                ? Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                              this.resource_growth.food)),
                           1)
                : 0;
        double perc_mat =
            (this.resource_growth.money < this.resource_growth.materials)
                ? Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                              this.resource_growth.materials)),
                           1)
                : 0;
        this.controller.configureMarket(market.getKey(),
                                        perc_food >= 0 ? perc_food : 0,
                                        perc_mat >= 0 ? perc_mat : 0);
        this.marketplaces.put(market.getKey(), true);
        if (!this.controller.actionPossible())
          return;
      }
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

    if (this.world.getHand().isEmpty() && event_empty_hand_start) {

      this.resource_growth_last = this.resource_growth;
    }

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
          "[delta] money=" + this.resource_growth_delta.money +
          " food=" + this.resource_growth_delta.food +
          " materials=" + this.resource_growth_delta.materials);

      System.out.println(
          "[perc] money=" + String.format("%.3f", this.resource_perc_money) +
          " food=" + String.format("%.3f", this.resource_perc_food) +
          " materials=" +
          String.format("%.3f", this.resource_perc_materials));
    }
  }
}
