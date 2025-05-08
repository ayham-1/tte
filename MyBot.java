/*
 *
 *                ACHTUNG/WARNING
 *         THIS IS NOT TO BE DESTRIBUTED
 *           IF YOU RECEIVED THIS FROM
 *               Ayham  Aboualfadl
 *     THROUGH ANY OF HIS COMMUNICATION MEANS,
 *     YOU ARE NOT ALLOWED TO FURTHER DISTRIBUTE
 *
 *
 *             Only valid throughout
 *               the AuD Competition
 *           After that, licensed under
 *                   GPL v3.0
 *
 *  :D
 * */

/*
 * Observations:
 *  - only one placement of a tile is allowed per turn
 *  - memory is more abundant than time!
 *  - the longer a tile is placed the better,
 *  - more tiles placed earlier == better
 *  - marketplaces convert food/material to money, and are actually very
 * important mid-game
 *  - after placing, the getMap() does not update
 *  - avoid() functionality with if ( && ) is not exactly most efficient
 *
 * Current:
 *  - only calculate relevant score for in range
 *  - director description:
 *    -  possible placement with its score is stored, and the director picks one
 *    - gets asked from groups whether their suggestions break rules
 *    - has hard set rules like:
 *        - don't have wheats greater than 9 ever
 *        - don't close wheat group
 *        - don't make windmills share the same wheat

 *      - decides whether to create a new group or not for a specific group of
 * g  roups
 *        - if (windmill + wheat > add wheat)
 *        - if (double + small + market > new double + market)
 *  - marketplaces use State class
 *
 * TODOs:
 *  - investigate sectors
 *    - what functions should sectors offer:
 *    - find a way to optimize multiple group placements
 *    - wheat/forest should also take in mind changes of score of other stuff
 * like beehives, windmills and marketplaces
 *  - remove need to have empty hand for any redraw event
 *
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
 *
 *  - avoid beehives closing forests too much
 *
 * PROBLEMATIC SEEDS:
 *  - 206291997783329192
 *  - 11841930331551995945
 *  - 15983273597267596929
 *  - 3670905267582609409
 *  - 3833241609646726404
 *  - 13643824703136673852
 *  - 4185536861074494110
 *  - 9404526250308863753
 *  - 7166311343533290299
 *  - 10657404372395100657
 *  - 1524678160230742459
 *
 * */

import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import page.codeberg.terratactician_expandoria.*;
import page.codeberg.terratactician_expandoria.bots.*;
import page.codeberg.terratactician_expandoria.world.CubeCoordinate;
import page.codeberg.terratactician_expandoria.world.Metrics;
import page.codeberg.terratactician_expandoria.world.tiles.*;
import page.codeberg.terratactician_expandoria.world.tiles.Tile.TileType;

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
    return "Bob the Bot the Benevolent Builder";
  }

  public enum ResourceType { Food, Materials, Money }

  class PlacementSuggestion implements Comparable<PlacementSuggestion> {
    Score growth_delta;
    CubeCoordinate coord = null;
    TileInfo info;

    PlacementSuggestion(TileType typ, CubeCoordinate coord, Score g_delta,
                        TileInfo info) {
      this.coord = coord;
      this.growth_delta = g_delta;
      this.info = info;
    }

    @Override
    public int compareTo(PlacementSuggestion other) {
      return this.growth_delta.compareTo(other.growth_delta);
    }
  };

  class TileInfo {
    TileType type;
    Group associated_group;
    TileRepr associated_repr;

    TileInfo(TileType t, Group grp) {
      this.type = t;
      this.associated_group = grp;
    }

    TileInfo(TileType t, TileRepr repr) {
      this.type = t;
      this.associated_repr = repr;
    }
  }

  class Score implements Comparable<Score> {
    double food;
    double materials;
    double money;

    Score(double food, double materials, double money) {
      this.food = food;
      this.materials = materials;
      this.money = money;
    }

    Score delta(Score other) {
      return new Score(this.food - other.food, this.materials - other.materials,
                       this.money - other.money);
    }

    void add(Score other) {
      this.food += other.food;
      this.materials += other.materials;
      this.money += other.money;
    }

    @Override
    public int compareTo(Score other) {
      return Double.compare((this.food + this.materials + this.money),
                            (other.food + other.materials + other.money));
    }

    public String toString() {
      return "food=" + this.food + " materials=" + this.materials +
          " money=" + this.money;
    }
  }

  abstract class Group {
    MyBot bot = null;

    HashMap<CubeCoordinate, TileType> tiles = new HashMap<>();
    Set<CubeCoordinate> coords_placable = new LinkedHashSet<>();

    Group(MyBot bot) { this.bot = bot; }

    ResourceType res_type;
    TileType type;

    abstract Score calc_score();

    abstract boolean accepts(TileType type);
    abstract boolean affects(TileType addition);

    boolean addable() { return !this.tiles.isEmpty(); }

    public Score calc_suggested_new_global_score(TileType newtype,
                                                 CubeCoordinate coord) {
      TileInfo info = new TileInfo(newtype, this);
      this.tiles.put(coord, newtype);
      this.bot.map.put(coord, new TileInfo(newtype, this));
      Score score = this.bot.director.get_relevant_score(info, coord);
      this.tiles.remove(coord);
      this.bot.map.remove(coord);
      return score;
    }

    public void suggest_group() {
      for (var cplacable : this.coords_placable) {
        TileInfo info = new TileInfo(this.type, this);
        this.bot.director.suggest(new PlacementSuggestion(
            this.type, cplacable,
            this.calc_suggested_new_global_score(this.type, cplacable)
                .delta(this.bot.director.get_relevant_score(info, cplacable)),
            info));
      }
    }

    public void suggest_all() {
      for (var cplacable : this.bot.coords_placable) {
        if (this.coords_placable.contains(cplacable))
          continue;

        TileInfo info = new TileInfo(this.type, this);
        Score s =
            this.calc_suggested_new_global_score(this.type, cplacable)
                .delta(this.bot.director.get_relevant_score(info, cplacable));
        s.add(this.calc_score());
        this.bot.director.suggest(
            new PlacementSuggestion(this.type, cplacable, s, info));
      }
    }

    public void add_tile(TileType type, CubeCoordinate coord) {
      this.tiles.put(coord, type);

      if (this.coords_placable.contains(coord))
        this.coords_placable.remove(coord);

      for (var cring : coord.getRing(1)) {
        if (world.getMap().at(cring) == null &&
            world.getBuildArea().contains(cring))
          this.coords_placable.add(cring);
      }
    }

    public void update_coords_placable() { this.update_coords_placable(null); }

    public void update_coords_placable(CubeCoordinate coord) {
      this.coords_placable = new LinkedHashSet<>();
      for (var c : tiles.keySet()) {
        for (var cring : c.getRing(1)) {
          if (!cring.equals(coord) &&
              this.bot.world.getMap().at(cring) == null &&
              this.bot.world.getBuildArea().contains(cring)) {
            this.coords_placable.add(cring);
          }
        }
      }
    }
  }

  class WheatGroup extends Group {
    /* Represents a Wheat group, optimal means:
     * - have 9 connected Wheat Tiles
     **/

    WheatGroup(MyBot bot) {
      super(bot);
      super.res_type = ResourceType.Food;
      super.type = TileType.Wheat;
    }

    public double calc_score_of_wheats(CubeCoordinate coord) {
      double w = (double)this.bot.group_count(TileType.Wheat, coord);
      double s = (w <= 9) ? 4.0f : -2.5f;
      double individual =
          (Math.pow(Math.E, -Math.pow(((w - 9.0f) / s), 2.0f)) * 2.4f + 0.1f);
      return w * individual;
    }

    @Override
    public Score calc_score() {
      double s = 0.0f;
      if (this.tiles.keySet().iterator().hasNext())
        s = this.calc_score_of_wheats(this.tiles.keySet().iterator().next());
      return new Score(s, 0.0f, 0.0f);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Wheat;
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai ||
          type == TileType.Wheat || type == TileType.Beehive;
    }
  }

  class ForestGroup extends Group {
    /* Represents a forest group, optimal means:
     * - most neighboring forest tiles
     * - group members all close together (implied)
     **/

    ForestGroup(MyBot bot) {
      super(bot);
      super.res_type = ResourceType.Materials;
      super.type = TileType.Forest;
    }

    public double calc_score_of_forest(CubeCoordinate start) {
      Set<CubeCoordinate> coords = new LinkedHashSet<CubeCoordinate>();
      this.bot.group_count_set(TileType.Forest, start, coords);
      double score = 0;
      for (var c : coords) {
        int count = 0;
        for (var cring : c.getRing(1)) {
          var tileinfo = this.bot.map.get(cring);
          if (tileinfo != null && tileinfo.type == TileType.Forest)
            count++;
        }
        score += count * 0.5f + 0.25;
      }
      return score;
    }

    @Override
    public Score calc_score() {
      double s = 0.0f;
      if (this.tiles.keySet().iterator().hasNext())
        s = this.calc_score_of_forest(this.tiles.keySet().iterator().next());
      return new Score(0.0f, s, 0.0f);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Forest;
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai ||
          type == TileType.Beehive;
    }
  }

  abstract class TileRepr {
    MyBot bot;
    CubeCoordinate coord;
    TileType type;

    TileRepr(MyBot bot, TileType type) {
      this.bot = bot;
      this.type = type;
    }

    // TileRepr has been accepted, so now remember position
    public void set_coord(CubeCoordinate coord) { this.coord = coord; }

    public Score calc_suggested_new_global_score(TileType newtype,
                                                 CubeCoordinate coord) {
      TileInfo info = new TileInfo(newtype, this);
      this.bot.map.put(coord, new TileInfo(newtype, this));
      Score score = this.bot.director.get_relevant_score(info, coord);
      this.bot.map.remove(coord);
      return score;
    }

    abstract Score calc_score(CubeCoordinate coord);
    Score calc_score() { return this.calc_score(this.coord); }

    abstract boolean affects(TileType type);

    public void suggest_all() {
      for (var cplacable : this.bot.coords_placable) {
        TileInfo info = new TileInfo(this.type, this);
        Score s =
            this.calc_suggested_new_global_score(this.type, cplacable)
                .delta(this.bot.director.get_relevant_score(info, cplacable));
        s.add(this.calc_score(cplacable));
        this.bot.director.suggest(
            new PlacementSuggestion(type, cplacable, s, info));
      }
    }

    public boolean in_range(CubeCoordinate coord, int distance) {
      if (this.coord.distance(coord) >= distance)
        return false;
      return true;
    }

    abstract boolean in_range(CubeCoordinate coord, TileType type);
  }

  class WindmillRepr extends TileRepr {
    WindmillRepr(MyBot bot) { super(bot, TileType.Windmill); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      int w = 0;
      for (var cneighbor : coord.getArea(3)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null || ct.type != TileType.Wheat)
          continue;

        int m = 0;
        for (var wheat_neighbor : coord.getArea(3)) {
          var wct = this.bot.map.get(wheat_neighbor);
          if (wct == null || wct.type != TileType.Windmill)
            continue;
          m++;
        }

        w += Math.min(1.0f, 2.0f / m);
      }

      int f = 0;
      for (var cneighbor : coord.getRing(1)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null || ct.type != TileType.Forest)
          continue;
        f++;
      }

      double score = 2.0f * w * (1.0f - (f * 0.5f));

      return new Score(score, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai ||
          type == TileType.Windmill;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      if (type == TileType.Wheat)
        return this.in_range(coord, 3);
      else if (type == TileType.Forest)
        return this.in_range(coord, 1);
      else
        return this.in_range(coord, 0);
    }
  }

  class BeehiveRepr extends TileRepr {
    BeehiveRepr(MyBot bot) { super(bot, TileType.Beehive); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      int f = 0;
      int w = 0;
      for (var cneighbor : coord.getArea(2)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null)
          continue;
        if (ct.type == TileType.Wheat)
          w++;
        if (ct.type == TileType.Forest)
          f++;
      }

      double score = Math.log((f + 1.0f) * (w + 1.0f)) * 0.6f;
      return new Score(score, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 2);
    }
  }

  class MoaiRepr extends TileRepr {
    CubeCoordinate coord;

    MoaiRepr(MyBot bot) { super(bot, TileType.Moai); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      Set<TileType> unique = new LinkedHashSet<>();
      for (var cneighbor : coord.getArea(4)) {
        var un = this.bot.map.get(cneighbor);
        if (un != null)
          unique.add(un.type);
      }
      double score = 1 + 0.5f * Math.max(0, unique.size() - 2);
      return new Score(score, score, score); // single score, ew
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.SmallHouse || type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 4);
    }
  }

  class MarketplaceRepr extends TileRepr {
    boolean configured = false;
    double perc_food = 0.0f;
    double perc_mat = 0.0f;

    MarketplaceRepr(MyBot bot) { super(bot, TileType.Marketplace); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      double f_rate = this.bot.market_get_food_rate();
      double m_rate = this.bot.market_get_materials_rate();

      int h = 0;
      int d = 0;
      for (var cring : coord.getRing(1)) {
        var t = this.bot.map.get(cring);
        if (t == null)
          continue;

        if (t.type == TileType.SmallHouse)
          h++;
        if (t.type == TileType.DoubleHouse)
          d++;
      }
      double e = Math.min(1, (h + (d * 2) * 0.4f + 0.2f));

      // TODO(ayham-1): implement this
      double f = 1.0f;
      double m = 1.0f;

      double score = (f * f_rate + m * m_rate) * e;

      return new Score(0.0f, 0.0f, score);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.SmallHouse ||
          type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 1);
    }
  }

  class DoubleHouseRepr extends TileRepr {
    DoubleHouseRepr(MyBot bot) { super(bot, TileType.DoubleHouse); }

    public double calc_score_of_dhouse(CubeCoordinate coord) {
      double best_boost = this.bot.get_best_boost(coord);
      int count = 0;
      for (var cring : coord.getRing(1)) {
        var tileinfo = this.bot.map.get(cring);
        if (tileinfo == null)
          continue;

        if (tileinfo.type == TileType.DoubleHouse ||
            tileinfo.type == TileType.SmallHouse)
          count++;
      }

      return (((-Math.abs(count - 3) + 3) / (2.0f / 3.0f)) + 0.5f) * best_boost;
    }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, this.calc_score_of_dhouse(coord));
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace ||
          type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 1);
    }
  }

  class SmallHouseRepr extends TileRepr {
    SmallHouseRepr(MyBot bot) { super(bot, TileType.SmallHouse); }

    public double calc_score_of_shouse(CubeCoordinate coord) {
      double best_boost = this.bot.get_best_boost(coord);
      return (2.0f * best_boost) + 2.0f;
    }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, this.calc_score_of_shouse(coord));
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 0);
    }
  }

  class GrassRepr extends TileRepr {
    GrassRepr(MyBot bot) { super(bot, TileType.Grass); }

    // lol by itself it does nothing, but added to the map, maybe
    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 0);
    }
  }

  class StoneRepr extends TileRepr {
    StoneRepr(MyBot bot, TileType stone) { super(bot, stone); }

    // lol by itself it does nothing, but added to the map, maybe
    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.StoneQuarry;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 0);
    }
  }

  class QuarryRepr extends TileRepr {
    QuarryRepr(MyBot bot) { super(bot, TileType.StoneQuarry); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      int max_l = 0;
      for (var cring : coord.getRing(1)) {
        var t = this.bot.map.get(cring);
        if (t == null)
          continue;

        if (t.type != TileType.StoneHill && t.type != TileType.StoneRocks &&
            t.type != TileType.StoneMountain)
          continue;

        int count_using_quarries = 0;
        for (var cquarry : cring.getRing(1)) {
          var tquarry = this.bot.map.get(cquarry);
          if (tquarry == null)
            continue;
          if (cquarry == coord)
            continue;
          if (tquarry.type == TileType.StoneQuarry)
            count_using_quarries++;
        }

        int l = 0;
        if (t.type == TileType.StoneMountain)
          l = 3;
        else if (t.type == TileType.StoneHill)
          l = 2;
        else if (t.type == TileType.StoneRocks)
          l = 1;

        if (t.type == TileType.StoneMountain && count_using_quarries <= 2)
          l = 3;
        else if (t.type == TileType.StoneHill && count_using_quarries <= 1)
          l = 2;
        else if (t.type == TileType.StoneRocks && count_using_quarries == 0)
          l = 1;

        if (l >= max_l)
          max_l = l;
      }

      double score = 5.0f * max_l;
      return new Score(0.0f, score, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.StoneQuarry;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      if (type == TileType.StoneMountain || type == TileType.StoneRocks ||
          type == TileType.StoneHill)
        return this.in_range(coord, 1);
      else
        return this.in_range(coord, 0);
    }
  }

  class State {
    // holds the various lists for the bot that are used to represent
    // information about the game map in a non Map<>'y fashion

    // the haves
    Set<Group> wheats = new LinkedHashSet<>();
    Set<Group> forests = new LinkedHashSet<>();
    Set<TileRepr> windmills = new LinkedHashSet<>();
    Set<TileRepr> quarries = new LinkedHashSet<>();
    Set<TileRepr> marketplaces = new LinkedHashSet<>();
    Set<TileRepr> dhouses = new LinkedHashSet<>();
    Set<TileRepr> shouses = new LinkedHashSet<>();
    Set<TileRepr> beehives = new LinkedHashSet<>();

    // the have-nots
    Set<TileRepr> moais = new LinkedHashSet<>();
    Set<TileRepr> grass = new LinkedHashSet<>();
    Set<TileRepr> stones = new LinkedHashSet<>();

    // current resource metrices
    Score current;
  }

  State state = new State();

  abstract class Director {
    MyBot bot;

    TileType chosen_tile;
    TreeSet<PlacementSuggestion> suggestions =
        new TreeSet<PlacementSuggestion>();

    abstract TileType get_preferred_tile(List<TileType> hand);
    abstract PlacementSuggestion pick();

    Director(MyBot bot) { this.bot = bot; }

    void new_card() { this.suggestions.clear(); }

    void suggest(PlacementSuggestion suggestion) {
      this.suggestions.add(suggestion);
    }

    void ask_for_suggestions() {
      TileType card = this.get_preferred_tile(this.bot.world.getHand().hand);

      Set<Group> group_set = null;
      Set<TileRepr> repr_set = null;

      // do creating new groups
      if (card == TileType.Marketplace) {
        repr_set = this.bot.state.marketplaces;
        MarketplaceRepr repr = new MarketplaceRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Grass) {
        repr_set = this.bot.state.grass;
        GrassRepr repr = new GrassRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.StoneHill) {
        repr_set = this.bot.state.stones;
        StoneRepr repr = new StoneRepr(this.bot, card);
        repr.suggest_all();
      } else if (card == TileType.StoneMountain) {
        repr_set = this.bot.state.stones;
        StoneRepr repr = new StoneRepr(this.bot, card);
        repr.suggest_all();
      } else if (card == TileType.StoneRocks) {
        repr_set = this.bot.state.stones;
        StoneRepr repr = new StoneRepr(this.bot, card);
        repr.suggest_all();
      } else if (card == TileType.Wheat) {
        group_set = this.bot.state.wheats;
        WheatGroup grp = new WheatGroup(this.bot);
        grp.suggest_all();
      } else if (card == TileType.Forest) {
        group_set = this.bot.state.forests;
        ForestGroup grp = new ForestGroup(this.bot);
        grp.suggest_all();
      } else if (card == TileType.DoubleHouse) {
        repr_set = this.bot.state.dhouses;
        DoubleHouseRepr repr = new DoubleHouseRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.SmallHouse) {
        repr_set = this.bot.state.shouses;
        SmallHouseRepr repr = new SmallHouseRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Windmill) {
        repr_set = this.bot.state.windmills;
        WindmillRepr repr = new WindmillRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Beehive) {
        repr_set = this.bot.state.beehives;
        BeehiveRepr repr = new BeehiveRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.StoneQuarry) {
        repr_set = this.bot.state.quarries;
        QuarryRepr repr = new QuarryRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Moai) {
        repr_set = this.bot.state.moais;
        MoaiRepr repr = new MoaiRepr(this.bot);
        repr.suggest_all();
      }

      if (group_set != null) {
        for (var group : group_set)
          if (group.addable())
            group.suggest_group();
      } else if (repr_set != null) {
        for (var repr : repr_set)
          repr.suggest_all();
      }
    }

    void accept_suggestion(PlacementSuggestion suggestion) {
      // i'm sorry for this atrocity...
      if (suggestion.info.associated_group != null) {
        if (suggestion.info.associated_group instanceof WheatGroup)
          this.bot.state.wheats.add(suggestion.info.associated_group);
        else if (suggestion.info.associated_group instanceof ForestGroup)
          this.bot.state.forests.add(suggestion.info.associated_group);
      } else if (suggestion.info.associated_repr != null) {
        if (suggestion.info.associated_repr instanceof WindmillRepr)
          this.bot.state.windmills.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof QuarryRepr)
          this.bot.state.quarries.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof MarketplaceRepr)
          this.bot.state.marketplaces.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof MoaiRepr)
          this.bot.state.moais.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof GrassRepr)
          this.bot.state.grass.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof StoneRepr)
          this.bot.state.stones.add(suggestion.info.associated_repr);
      }

      if (suggestion.info.associated_group != null)
        suggestion.info.associated_group.add_tile(suggestion.info.type,
                                                  suggestion.coord);

      if (suggestion.info.associated_repr != null)
        suggestion.info.associated_repr.set_coord(suggestion.coord);

      this.bot.place_tile(suggestion.coord, suggestion.info);
    }

    Score get_global_score() {
      Score score = new Score(0.0f, 0.0f, 0.0f);

      for (var wheat_grp : this.bot.state.wheats)
        score.add(wheat_grp.calc_score());

      for (var forest_grp : this.bot.state.forests)
        score.add(forest_grp.calc_score());

      for (var windmill : this.bot.state.windmills)
        score.add(windmill.calc_score());

      for (var quarry : this.bot.state.quarries)
        score.add(quarry.calc_score());

      for (var marketplace : this.bot.state.marketplaces)
        score.add(marketplace.calc_score());

      return score;
    };

    Score get_relevant_score(TileInfo info, CubeCoordinate coord) {
      Score score = new Score(0.0f, 0.0f, 0.0f);

      // TODO(ayham-1): moai
      if (info.associated_repr != null) {
        if (info.associated_repr.affects(TileType.Wheat))
          for (var wheat_grp : this.bot.state.wheats)
            if (wheat_grp.addable())
              score.add(wheat_grp.calc_score());

        if (info.associated_repr.affects(TileType.Forest))
          for (var forest_grp : this.bot.state.forests)
            if (forest_grp.addable())
              score.add(forest_grp.calc_score());

        if (info.associated_repr.affects(TileType.Windmill))
          for (var windmill : this.bot.state.windmills)
            if (windmill.in_range(coord, info.type))
              score.add(windmill.calc_score());

        if (info.associated_repr.affects(TileType.StoneQuarry))
          for (var quarry : this.bot.state.quarries)
            if (quarry.in_range(coord, info.type))
              score.add(quarry.calc_score());

        if (info.associated_repr.affects(TileType.Marketplace))
          for (var marketplace : this.bot.state.marketplaces)
            if (marketplace.in_range(coord, info.type))
              score.add(marketplace.calc_score());
      } else if (info.associated_group != null) {
        if (info.associated_group.affects(TileType.Wheat))
          for (var wheat_grp : this.bot.state.wheats)
            if (wheat_grp.addable())
              score.add(wheat_grp.calc_score());

        if (info.associated_group.affects(TileType.Forest))
          for (var forest_grp : this.bot.state.forests)
            if (forest_grp.addable())
              score.add(forest_grp.calc_score());

        if (info.associated_group.affects(TileType.Windmill))
          for (var windmill : this.bot.state.windmills)
            if (windmill.in_range(coord, info.type))
              score.add(windmill.calc_score());

        if (info.associated_group.affects(TileType.StoneQuarry))
          for (var quarry : this.bot.state.quarries)
            if (quarry.in_range(coord, info.type))
              score.add(quarry.calc_score());

        if (info.associated_group.affects(TileType.Marketplace))
          for (var marketplace : this.bot.state.marketplaces)
            if (marketplace.in_range(coord, info.type))
              score.add(marketplace.calc_score());
      }

      return score;
    };
  }

  class GreedyDictator extends Director {
    /* Greedy dictator, just selects the placement with highest score delta,
     * no consideration for the future, rules with no rules */

    GreedyDictator(MyBot bot) { super(bot); }

    @Override
    TileType get_preferred_tile(List<TileType> hand) {
      ResourceType needed = ResourceType.Money;
      if (this.bot.resource_growth.money <= this.bot.resource_growth.food &&
          this.bot.resource_growth.money <=
              this.bot.resource_growth.materials) {
        needed = ResourceType.Money;
      } else if (this.bot.resource_growth.food <=
                     this.bot.resource_growth.money &&
                 this.bot.resource_growth.food <=
                     this.bot.resource_growth.materials) {
        needed = ResourceType.Food;
      } else if (this.bot.resource_growth.materials <=
                     this.bot.resource_growth.money &&
                 this.bot.resource_growth.materials <=
                     this.bot.resource_growth.food) {
        needed = ResourceType.Materials;
      }

      for (var card : hand) {
        if (this.bot.get_resource_type(card) == needed)
          return card;
      }

      return hand.iterator().next();
    }

    @Override
    PlacementSuggestion pick() {
      System.out.println(this.suggestions.last().growth_delta.toString());
      return this.suggestions.last();
    }
  }

  class StrictDictator extends Director {
    /* Greedy dictator, just selects the placement with highest score delta,
     * no consideration for the future, rules with no rules */

    StrictDictator(MyBot bot) { super(bot); }

    @Override
    TileType get_preferred_tile(List<TileType> hand) {
      ResourceType needed = ResourceType.Money;
      if (this.bot.resource_growth.money <= this.bot.resource_growth.food &&
          this.bot.resource_growth.money <=
              this.bot.resource_growth.materials) {
        needed = ResourceType.Money;
      } else if (this.bot.resource_growth.food <=
                     this.bot.resource_growth.money &&
                 this.bot.resource_growth.food <=
                     this.bot.resource_growth.materials) {
        needed = ResourceType.Food;
      } else if (this.bot.resource_growth.materials <=
                     this.bot.resource_growth.money &&
                 this.bot.resource_growth.materials <=
                     this.bot.resource_growth.food) {
        needed = ResourceType.Materials;
      }

      for (var card : hand) {
        if (this.bot.get_resource_type(card) == needed)
          return card;
      }

      return hand.iterator().next();
    }

    @Override
    PlacementSuggestion pick() {
      return this.suggestions.last();
    }
  }

  Director director = new GreedyDictator(this);

  // API vars
  World world;
  Controller controller;

  //// state vars
  int round = 0;
  boolean is_first = true;
  boolean must_win = false;
  boolean redrawn = false;

  // track the map ourselves
  Map<CubeCoordinate, TileInfo> map = new LinkedHashMap<>();

  /* placable coords that are not-isolated, manually updated every placed
   * tile and every round change */
  Set<CubeCoordinate> coords_placable = new LinkedHashSet<>();
  HashMap<CubeCoordinate, Boolean> marketplaces = new HashMap<>();

  /* per hand suggestions per resource type */
  Set<PlacementSuggestion> suggests_food = new LinkedHashSet<>();
  Set<PlacementSuggestion> suggests_materials = new LinkedHashSet<>();
  Set<PlacementSuggestion> suggests_money = new LinkedHashSet<>();
  Set<PlacementSuggestion> suggests_grass = new LinkedHashSet<>(); // ugh

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

    // track lives
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

    // calculate per hand growth delta
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

    // always redraw when free
    if (world.getHand().isEmpty() && world.getRedrawTime() <= 0) {
      controller.redraw();
      return;
    }

    // always collect rewards no matter what
    for (var reward : world.getRewards()) {
      controller.collectReward(reward.getCoordinate());
      if (!controller.actionPossible())
        break;
    }
    if (!controller.actionPossible())
      return;

    // force update marketplaces when hand is empty
    if (this.world.getHand().isEmpty()) {
      if (!this.marketplaces.values().contains(false)) // lol maybe use variable
        for (var market : this.marketplaces.keySet())
          this.marketplaces.put(market, false);

      // only update marketplaces if hand is empty, don't block placements
      this.setup_marketplaces();
    }

    if (!this.controller.actionPossible())
      return;

    if (world.getHand().isEmpty()) {
      // determine if we need to redraw since we are not hitting the target
      // resources [CURRENTLY DISABLED]
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

          double early_bias =
              1 - (Math.pow(Math.E,
                            -((this.round +
                               (this.world.getRoundTime() / 60.0f) * 2.5))));

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

    // make world zero as first tile
    if (is_first) {
      this.coords_placable.add(new CubeCoordinate());
      is_first = false;
    }

    this.director.new_card();
    if (!this.world.getHand().isEmpty() && !this.coords_placable.isEmpty()) {
      this.director.ask_for_suggestions();
      this.director.accept_suggestion(this.director.pick());
    }

    // Score crnt = this.director.get_global_score();
    // System.out.println(crnt.toString());
  }

  void place_marketplace() {}

  void place_grass() {}

  void place_stonehill() {}

  void place_stonemountain() {}

  void place_stonerocks() {}

  void place_wheat() {}

  void place_forest() {}

  void place_doublehouse() {}

  void place_smallhouse() {}

  void place_windmill() {}

  void place_beehive() {}

  void place_stonequarry() {}

  void place_moai() {}

  boolean place_tile(CubeCoordinate coord, TileInfo info) {
    System.out.println("want " + info.type + " on " + coord);
    if (coord == null)
      return false;
    System.out.println("is_coord_usable: " +
                       (this.world.getMap().at(coord) == null));
    if (world.getMap().at(coord) != null)
      return false;

    if (!world.getBuildArea().contains(coord))
      return false;

    try {
      controller.placeTile(info.type, coord); // the heart of it all...
    } catch (Exception e) {
      System.out.println("SOMETHING VERY BAD HAPPENED HELP HELP: " +
                         e.toString());
    } finally {
      this.map.put(coord, info);
      // world does not actually update when calling controller.placeTile() in
      // the same turn
      for (var cring : coord.getRing(1)) {
        if (world.getMap().at(cring) == null &&
            world.getBuildArea().contains(cring))
          this.coords_placable.add(cring);
      }

      if (this.coords_placable.contains(coord))
        this.coords_placable.remove(coord);

      for (var wheat_group : this.state.wheats)
        wheat_group.update_coords_placable(coord);
      for (var forest_group : this.state.forests)
        forest_group.update_coords_placable(coord);
    }

    // this.coords_placable.removeIf(t -> !world.getBuildArea().contains(t));
    return true;
  }

  // TODO(ayham-1): track moai tiles separately for quick access
  double get_best_boost(CubeCoordinate coord) {
    // TODO(ayham-1): put this in grouping
    double boost = 0.0f;
    for (var cring : coord.getArea(3)) {
      var crt = this.map.get(cring);
      if (crt == null)
        continue;

      double current_boost = 0.0f;
      if (crt.type == TileType.Moai) {
        Set<TileType> unique = new LinkedHashSet<>();
        for (var cneighbor : cring.getArea(4)) {
          var un = this.map.get(cneighbor);
          if (un != null)
            unique.add(un.type);
        }
        current_boost = 1 + 0.5f * Math.max(0, unique.size() - 2);
        if (current_boost >= boost)
          boost = current_boost;
      }
    }

    return boost;
  }

  void update_coords_placable() {
    for (var c : this.world.getBuildArea()) {
      if (world.getMap().at(c) != null)
        continue;
      if (world.getMap().getNeighbors(c).hasNext() == true)
        this.coords_placable.add(c);
    }

    for (var wheat_group : this.state.wheats)
      wheat_group.update_coords_placable();
    for (var forest_group : this.state.forests)
      forest_group.update_coords_placable();
  }

  double market_get_food_rate() {
    double perc_food = 0.0f;
    if (this.resource_current.money >= this.resource_current.food)
      return perc_food;
    perc_food = Math.pow(Math.E, -2 * ((this.resource_current.money /
                                        this.resource_current.food)));
    return Math.clamp(perc_food, 0.0f, 1.0f);
  }

  double market_get_materials_rate() {
    double perc_mat = 0.0f;
    if (this.resource_current.money >= this.resource_current.materials)
      return perc_mat;
    perc_mat = Math.pow(Math.E, -2 * ((this.resource_current.money /
                                       this.resource_current.materials)));
    return Math.clamp(perc_mat, 0.0f, 1.0f);
  }

  void setup_marketplaces() {
    for (var repr : this.state.marketplaces) {
      MarketplaceRepr market = (MarketplaceRepr)repr;
      if (market.configured == false) {
        double perc_food = this.market_get_food_rate();
        double perc_mat = this.market_get_materials_rate();

        this.controller.configureMarket(market.coord, perc_food, perc_mat);
        market.perc_food = perc_food;
        market.perc_mat = perc_mat;
        market.configured = true;
        if (!this.controller.actionPossible())
          return;
      }
    }
  }

  void group_count_set(TileType type, CubeCoordinate start,
                       Set<CubeCoordinate> coords) {

    if (this.map.get(start) == null)
      return;

    if (this.map.get(start).type == type) {
      coords.add(start);
    }

    for (var cneighbor : start.getRing(1)) {
      var tneighbor = this.map.get(cneighbor);
      if (tneighbor == null)
        continue;

      if (tneighbor.type == type) {
        if (!coords.contains(cneighbor)) {
          coords.add(cneighbor);
          group_count_set(type, cneighbor, coords);
        }
      }
    }
  }

  int group_count(TileType type, CubeCoordinate start) {
    Set<CubeCoordinate> coord = new LinkedHashSet<CubeCoordinate>();
    group_count_set(type, start, coord);

    return coord.size();
  }

  ResourceType get_resource_type(TileType type) {
    if (type == TileType.Wheat || type == TileType.Beehive ||
        type == TileType.Windmill)
      return ResourceType.Food;
    else if (type == TileType.StoneHill || type == TileType.StoneRocks ||
             type == TileType.StoneMountain || type == TileType.StoneQuarry)
      return ResourceType.Materials;
    else
      return ResourceType.Money;
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
