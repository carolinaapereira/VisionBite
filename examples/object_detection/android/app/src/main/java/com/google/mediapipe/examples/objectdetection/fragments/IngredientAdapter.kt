import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.objectdetection.R

class IngredientAdapter(
    private val ingredients: MutableList<String>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder>() {

    class IngredientViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIngredient: TextView = view.findViewById(R.id.tvIngredient)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient, parent, false)
        return IngredientViewHolder(view)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.tvIngredient.text = ingredients[position]
        holder.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    fun addItem(element: String){
        val position = ingredients.size
        ingredients.add(element)
        notifyItemInserted(position)
        notifyItemRangeChanged(position, ingredients.size)
    }
    fun removeItem(position: Int) {
        ingredients.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, ingredients.size)
    }

    override fun getItemCount(): Int {
        return ingredients.size
    }
}