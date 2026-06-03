package app.freerouting.settings

import app.freerouting.autoroute.AutorouteControl
import app.freerouting.board.RoutingBoard
import app.freerouting.logger.FRLogger
import app.freerouting.management.ReflectionUtil
import com.google.gson.annotations.SerializedName
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.io.Serializable

class RouterSettings : Serializable, Cloneable {

    @SerializedName("enabled")
    @JvmField
    var enabled: Boolean? = null

    @SerializedName("algorithm")
    @JvmField
    var algorithm: String? = null

    /** Configuration for the SMD-pin fanout pre-pass. */
    @SerializedName("fanout")
    @JvmField
    var fanout: FanoutSettings? = null

    @SerializedName("copper_to_edge_clearance_um")
    @JvmField
    var copperToEdgeClearanceUm: Double? = null

    @SerializedName("job_timeout")
    @JvmField
    var jobTimeoutString: String? = null

    @SerializedName("max_passes")
    @JvmField
    var maxPasses: Int? = null

    @SerializedName("max_items")
    @Transient
    @JvmField
    var maxItems: Int? = null

    @Transient
    @JvmField
    var isLayerActive: BooleanArray? = null

    @Transient
    @JvmField
    var isPreferredDirectionHorizontalOnLayer: BooleanArray? = null

    @Transient
    @JvmField
    var save_intermediate_stages: Boolean? = false

    @SerializedName("ignore_net_classes")
    @Transient
    @JvmField
    var ignoreNetClasses: Array<String>? = null

    /**
     * The accuracy of the pull tight algorithm.
     */
    @SerializedName("trace_pull_tight_accuracy")
    @JvmField
    var trace_pull_tight_accuracy: Int? = null

    @SerializedName("allowed_via_types")
    @JvmField
    var vias_allowed: Boolean? = null

    /**
     * If true, the trace width at static pins smaller the trace width will be
     * lowered automatically to the pin with, if necessary.
     */
    @SerializedName("automatic_neckdown")
    @JvmField
    var automatic_neckdown: Boolean? = null

    @SerializedName("optimizer")
    @JvmField
    var optimizer: RouterOptimizerSettings? = null

    @SerializedName("scoring")
    @JvmField
    var scoring: RouterScoringSettings? = null

    @SerializedName("max_threads")
    @JvmField
    var maxThreads: Int? = null

    // PropertyChangeSupport for bidirectional binding with GUI
    @Transient
    private var pcs: PropertyChangeSupport? = PropertyChangeSupport(this)

    companion object {
        const val ALGORITHM_CURRENT = "freerouting-router"
        const val ALGORITHM_V19 = "freerouting-router-v19"
    }

    /**
     * Parameterless constructor for serialization.
     * Initializes nested settings objects.
     */
    constructor() {
        this.optimizer = RouterOptimizerSettings()
        this.scoring = RouterScoringSettings()
        this.fanout = FanoutSettings()
    }

    /**
     * Creates a new instance of AutorouteSettings
     */
    constructor(p_board: RoutingBoard) : this() {
        setLayerCount(p_board.get_layer_count())
        applyBoardSpecificOptimizations(p_board)
    }

    // PropertyChangeListener support for bidirectional binding
    fun addPropertyChangeListener(listener: PropertyChangeListener) {
        if (pcs == null) {
            pcs = PropertyChangeSupport(this)
        }
        pcs!!.addPropertyChangeListener(listener)
    }

    fun removePropertyChangeListener(listener: PropertyChangeListener) {
        pcs?.removePropertyChangeListener(listener)
    }

    // Setter methods that fire property change events for bidirectional binding
    fun setMaxPasses(value: Int?) {
        val oldValue = this.maxPasses
        this.maxPasses = value
        pcs?.firePropertyChange("maxPasses", oldValue, value)
    }

    fun setMaxThreads(value: Int?) {
        val oldValue = this.maxThreads
        this.maxThreads = value
        pcs?.firePropertyChange("maxThreads", oldValue, value)
        // Also update optimizer's maxThreads to keep them in sync
        optimizer?.let {
            it.maxThreads = value
        }
    }

    fun setJobTimeoutString(value: String?) {
        val oldValue = this.jobTimeoutString
        this.jobTimeoutString = value
        pcs?.firePropertyChange("jobTimeoutString", oldValue, value)
    }

    fun setEnabled(value: Boolean?) {
        val oldValue = this.enabled
        this.enabled = value
        pcs?.firePropertyChange("enabled", oldValue, value)
    }

    fun setViasAllowed(value: Boolean?) {
        val oldValue = this.vias_allowed
        this.vias_allowed = value
        pcs?.firePropertyChange("vias_allowed", oldValue, value)
    }

    fun setAlgorithm(value: String?) {
        val oldValue = this.algorithm
        this.algorithm = value
        pcs?.firePropertyChange("algorithm", oldValue, value)
    }

    fun setOptimizerEnabled(value: Boolean?) {
        val oldValue = optimizer?.enabled
        optimizer?.let {
            it.enabled = value
        }
        pcs?.firePropertyChange("optimizer.enabled", oldValue, value)
    }

    /**
     * Apply board-specific optimizations to RouterSettings based on board geometry
     * and layer structure.
     * This calculates layer costs based on board aspect ratio and adds penalties
     * for outer layers.
     * Should be called after loading a board to optimize routing performance.
     *
     * @param p_board The routing board to optimize settings for
     */
    fun applyBoardSpecificOptimizations(p_board: RoutingBoard) {
        val horizontal_width = p_board.bounding_box.width()
        val vertical_width = p_board.bounding_box.height()
        val layer_count = p_board.get_layer_count()

        val horizontal_add_costs_against_preferred_dir = 0.1 * Math.round(10.0 * horizontal_width / vertical_width).toDouble()
        val vertical_add_costs_against_preferred_dir = 0.1 * Math.round(10.0 * vertical_width / horizontal_width).toDouble()

        var curr_preferred_direction_is_horizontal = horizontal_width < vertical_width

        // initialize the layer specific settings.
        if (isLayerActive == null || isLayerActive!!.size != layer_count) {
            isLayerActive = BooleanArray(layer_count)
        }
        if (isPreferredDirectionHorizontalOnLayer == null || isPreferredDirectionHorizontalOnLayer!!.size != layer_count) {
            isPreferredDirectionHorizontalOnLayer = BooleanArray(layer_count)
        }
        
        val scoringObj = scoring ?: RouterScoringSettings().also { scoring = it }
        
        if (scoringObj.preferredDirectionTraceCost == null || scoringObj.preferredDirectionTraceCost!!.size != layer_count) {
            scoringObj.preferredDirectionTraceCost = DoubleArray(layer_count)
        }
        if (scoringObj.undesiredDirectionTraceCost == null || scoringObj.undesiredDirectionTraceCost!!.size != layer_count) {
            scoringObj.undesiredDirectionTraceCost = DoubleArray(layer_count)
        }

        if (scoringObj.defaultPreferredDirectionTraceCost == null) {
            scoringObj.defaultPreferredDirectionTraceCost = 1.0
        }
        if (scoringObj.defaultUndesiredDirectionTraceCost == null) {
            scoringObj.defaultUndesiredDirectionTraceCost = 1.0
        }

        val preferredDirectionTraceCost = scoringObj.preferredDirectionTraceCost!!
        val undesiredDirectionTraceCost = scoringObj.undesiredDirectionTraceCost!!

        for (i in 0 until layer_count) {
            isLayerActive!![i] = p_board.layer_structure.arr[i].is_signal
            if (p_board.layer_structure.arr[i].is_signal) {
                curr_preferred_direction_is_horizontal = !curr_preferred_direction_is_horizontal
            }
            isPreferredDirectionHorizontalOnLayer!![i] = curr_preferred_direction_is_horizontal
            preferredDirectionTraceCost[i] = scoringObj.defaultPreferredDirectionTraceCost!!
            undesiredDirectionTraceCost[i] = scoringObj.defaultUndesiredDirectionTraceCost!!
            if (curr_preferred_direction_is_horizontal) {
                undesiredDirectionTraceCost[i] += horizontal_add_costs_against_preferred_dir
            } else {
                undesiredDirectionTraceCost[i] += vertical_add_costs_against_preferred_dir
            }
        }
        
        val signal_layer_count = p_board.layer_structure.signal_layer_count()
        if (signal_layer_count > 2) {
            val outer_add_costs = 0.2 * signal_layer_count
            preferredDirectionTraceCost[0] += outer_add_costs
            preferredDirectionTraceCost[layer_count - 1] += outer_add_costs
            undesiredDirectionTraceCost[0] += outer_add_costs
            undesiredDirectionTraceCost[layer_count - 1] += outer_add_costs
        }
    }

    /**
     * Get the number of layers configured in the router settings.
     *
     * @return The layer count
     */
    fun getLayerCount(): Int {
        return isLayerActive?.size ?: 0
    }

    /**
     * Set the layer count and initialize the layer specific settings.
     */
    fun setLayerCount(layerCount: Int) {
        isLayerActive = BooleanArray(layerCount)
        isPreferredDirectionHorizontalOnLayer = BooleanArray(layerCount)
        
        val scoringObj = scoring ?: RouterScoringSettings().also { scoring = it }
        scoringObj.preferredDirectionTraceCost = DoubleArray(layerCount)
        scoringObj.undesiredDirectionTraceCost = DoubleArray(layerCount)

        for (i in 0 until layerCount) {
            isLayerActive!![i] = true
            isPreferredDirectionHorizontalOnLayer!![i] = i % 2 == 1
            scoringObj.preferredDirectionTraceCost!![i] = 1.0
            scoringObj.undesiredDirectionTraceCost!![i] = 1.0
        }
    }

    public override fun clone(): RouterSettings {
        val result = RouterSettings()
        val layerCount = this.getLayerCount()
        if (layerCount > 0) {
            result.setLayerCount(layerCount)
        }
        result.algorithm = this.algorithm
        result.jobTimeoutString = this.jobTimeoutString
        result.isLayerActive = this.isLayerActive?.clone()
        result.isPreferredDirectionHorizontalOnLayer = this.isPreferredDirectionHorizontalOnLayer?.clone()
        result.maxPasses = this.maxPasses
        result.maxItems = this.maxItems
        result.copperToEdgeClearanceUm = this.copperToEdgeClearanceUm
        result.ignoreNetClasses = this.ignoreNetClasses?.clone()
        result.trace_pull_tight_accuracy = this.trace_pull_tight_accuracy
        result.enabled = this.enabled
        result.vias_allowed = this.vias_allowed
        result.automatic_neckdown = this.automatic_neckdown
        result.maxThreads = this.maxThreads

        result.optimizer = this.optimizer?.clone()
        result.scoring = this.scoring?.clone()
        result.fanout = this.fanout?.clone() ?: FanoutSettings()

        return result
    }

    fun get_start_ripup_costs(): Int {
        return scoring?.startRipupCosts ?: 1
    }

    fun set_start_ripup_costs(p_value: Int) {
        scoring?.let {
            it.startRipupCosts = Math.max(p_value, 1)
        }
    }

    fun getRunRouter(): Boolean {
        return enabled ?: true
    }

    fun setRunRouter(p_value: Boolean) {
        enabled = p_value
    }

    fun getRunOptimizer(): Boolean {
        return optimizer?.enabled ?: false
    }

    fun setRunOptimizer(p_value: Boolean) {
        val opt = optimizer ?: RouterOptimizerSettings().also { optimizer = it }
        opt.enabled = p_value
    }

    fun isFanoutEnabled(): Boolean {
        return fanout?.enabled == true
    }

    fun get_vias_allowed(): Boolean {
        return vias_allowed ?: true
    }

    fun set_vias_allowed(p_value: Boolean) {
        vias_allowed = p_value
    }

    fun get_via_costs(): Int {
        return scoring?.viaCosts ?: 1
    }

    fun set_via_costs(p_value: Int) {
        scoring?.let {
            it.viaCosts = Math.max(p_value, 1)
        }
    }

    fun get_plane_via_costs(): Int {
        return scoring?.planeViaCosts ?: 1
    }

    fun set_plane_via_costs(p_value: Int) {
        scoring?.let {
            it.planeViaCosts = Math.max(p_value, 1)
        }
    }

    fun set_layer_active(p_layer: Int, p_value: Boolean) {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.set_layer_active: p_layer=$p_layer out of range [0..${layerCount - 1}]")
            return
        }
        isLayerActive?.let { it[p_layer] = p_value }
    }

    fun get_layer_active(p_layer: Int): Boolean {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.get_layer_active: p_layer=$p_layer out of range [0..${layerCount - 1}]")
            return false
        }
        return isLayerActive?.get(p_layer) ?: false
    }

    fun set_preferred_direction_is_horizontal(p_layer: Int, p_value: Boolean) {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.set_preferred_direction_is_horizontal: p_layer=$p_layer out of range [0..${layerCount - 1}]")
            return
        }
        isPreferredDirectionHorizontalOnLayer?.let { it[p_layer] = p_value }
    }

    fun get_preferred_direction_is_horizontal(p_layer: Int): Boolean {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.get_preferred_direction_is_horizontal: p_layer=$p_layer out of range [0..${layerCount - 1}]")
            return false
        }
        return isPreferredDirectionHorizontalOnLayer?.get(p_layer) ?: false
    }

    fun set_preferred_direction_trace_costs(p_layer: Int, p_value: Double) {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.set_preferred_direction_trace_costs: p_layer out of range")
            return
        }
        scoring?.preferredDirectionTraceCost?.let {
            it[p_layer] = Math.max(p_value, 0.1)
        }
    }

    fun get_preferred_direction_trace_costs(p_layer: Int): Double {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range")
            return 0.0
        }
        return scoring?.preferredDirectionTraceCost?.get(p_layer) ?: 0.0
    }

    fun get_against_preferred_direction_trace_costs(p_layer: Int): Double {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range")
            return 0.0
        }
        return scoring?.undesiredDirectionTraceCost?.get(p_layer) ?: 0.0
    }

    fun get_horizontal_trace_costs(p_layer: Int): Double {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range")
            return 0.0
        }
        val isHorizontal = isPreferredDirectionHorizontalOnLayer?.get(p_layer) ?: false
        return if (isHorizontal) {
            scoring?.preferredDirectionTraceCost?.get(p_layer) ?: 0.0
        } else {
            scoring?.undesiredDirectionTraceCost?.get(p_layer) ?: 0.0
        }
    }

    fun set_against_preferred_direction_trace_costs(p_layer: Int, p_value: Double) {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.set_against_preferred_direction_trace_costs: p_layer out of range")
            return
        }
        scoring?.undesiredDirectionTraceCost?.let {
            it[p_layer] = Math.max(p_value, 0.1)
        }
    }

    fun get_vertical_trace_costs(p_layer: Int): Double {
        val layerCount = this.getLayerCount()
        if (p_layer < 0 || p_layer >= layerCount) {
            FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range")
            return 0.0
        }
        val isHorizontal = isPreferredDirectionHorizontalOnLayer?.get(p_layer) ?: false
        return if (isHorizontal) {
            scoring?.undesiredDirectionTraceCost?.get(p_layer) ?: 0.0
        } else {
            scoring?.preferredDirectionTraceCost?.get(p_layer) ?: 0.0
        }
    }

    fun get_trace_cost_arr(): Array<AutorouteControl.ExpansionCostFactor> {
        val prefCosts = scoring?.preferredDirectionTraceCost ?: return emptyArray()
        return Array(prefCosts.size) { i ->
            AutorouteControl.ExpansionCostFactor(get_horizontal_trace_costs(i), get_vertical_trace_costs(i))
        }
    }

    /**
     * If true, the trace width at static pins smaller the trace width will be
     * lowered automatically to the pin with, if necessary.
     */
    fun get_automatic_neckdown(): Boolean {
        return this.automatic_neckdown ?: false
    }

    /**
     * If true, the trace width at static pins smaller the trace width will be
     * lowered automatically to the pin with, if necessary.
     */
    fun set_automatic_neckdown(p_value: Boolean) {
        this.automatic_neckdown = p_value
    }

    /**
     * Applies new values from the given settings to this settings object.
     * Uses reflection to copy only non-null fields from the source settings.
     */
    fun applyNewValuesFrom(settings: RouterSettings?): Int {
        if (settings == null) {
            FRLogger.warn("Attempted to apply null settings, skipping")
            return 0
        }

        val changedCount = ReflectionUtil.copyFields(settings, this)

        pcs?.let {
            it.firePropertyChange("maxPasses", null, this.maxPasses)
            it.firePropertyChange("maxThreads", null, this.maxThreads)
            it.firePropertyChange("jobTimeoutString", null, this.jobTimeoutString)
            it.firePropertyChange("enabled", null, this.enabled)
        }

        return changedCount
    }

    fun validate() {
        // Validate maxPasses (0 means no limit)
        val passes = this.maxPasses
        if (passes == null || passes < 0 || passes > 9999) {
            FRLogger.warn("Invalid maxPasses value: $passes, using default 9999")
            this.maxPasses = 9999
        } else if (passes == 0) {
            // 0 means no limit, set to maximum
            this.maxPasses = Int.MAX_VALUE
            FRLogger.debug("maxPasses set to 0 (no limit), using Integer.MAX_VALUE")
        }

        // Validate maxThreads (0 means no limit - will be handled as max available)
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val threads = this.maxThreads
        if (threads == null || threads < 0 || threads > availableProcessors) {
            val defaultThreads = Math.max(1, availableProcessors - 1)
            FRLogger.warn("Invalid maxThreads value: $threads, using $defaultThreads")
            this.maxThreads = defaultThreads
        } else if (threads == 0) {
            // 0 means no limit, use all available processors minus 1
            this.maxThreads = Math.max(1, availableProcessors - 1)
            FRLogger.debug("maxThreads set to 0 (no limit), using ${this.maxThreads} threads")
        }

        // Validate trace_pull_tight_accuracy
        val accuracy = this.trace_pull_tight_accuracy
        if (accuracy == null || accuracy < 1) {
            FRLogger.warn("Invalid trace_pull_tight_accuracy value: $accuracy, using default 500")
            this.trace_pull_tight_accuracy = 500
        }
    }
}
