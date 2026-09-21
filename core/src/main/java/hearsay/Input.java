package hearsay;

/**
 * Something done to the village from outside it: planting a rumor, reporting who was seen
 * where, and selling a villager something.
 *
 * <p>Inputs are not events. An event is a record of what happened, with every id already
 * decided; an input is a request, and the simulation turns it into an event when the tick
 * comes round. Keeping them apart is what lets a counterfactual re-run from seed + params
 * + inputs and have everything derived decided again.
 */
public sealed interface Input permits PlantRumor, ObservedMeeting, VillagerSeen, PlayerTraded, RealityChecked {
    long tick();
}
