package com.example.p10;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.Document;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Databases;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

    private final List<Map<String, Object>> lista = new ArrayList<>();
    private final Client client;
    private final String userId;
    private final AppViewModel appViewModel;
    private final NavControllerProvider navProvider;
    private final OnPostsUpdatedListener postsUpdatedListener;

    public interface NavControllerProvider {
        void navigate(int resId, Bundle bundle);
    }

    public interface OnPostsUpdatedListener {
        void actualizarPosts();
    }

    public PostsAdapter(Client client, String userId, AppViewModel appViewModel, NavControllerProvider navProvider, OnPostsUpdatedListener listener) {
        this.client = client;
        this.userId = userId;
        this.appViewModel = appViewModel;
        this.navProvider = navProvider;
        this.postsUpdatedListener = listener;
    }

    public void establecerLista(List<Document<Map<String, Object>>> documentList) {
        lista.clear();
        for (Document<Map<String, Object>> document : documentList) {
            Map<String, Object> data = document.getData();
            if (data != null) {
                data.put("$id", document.getId());
                lista.add(data);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.viewholder_post, parent, false);
        return new PostViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final PostViewHolder holder, final int position) {
        final Map<String, Object> post = lista.get(position);
        final String postId = post.get("$id").toString();
        final String postAuthorId = post.get("uid") != null ? post.get("uid").toString() : "";
        final Context context = holder.itemView.getContext();

        holder.authorTextView.setText(post.get("author").toString());
        if (post.get("authorPhotoUrl") == null) {
            holder.authorPhotoImageView.setImageResource(R.drawable.user);
        } else {
            Glide.with(context)
                    .load(post.get("authorPhotoUrl").toString())
                    .circleCrop()
                    .into(holder.authorPhotoImageView);
        }

        String mensaje = post.get("content") != null ? post.get("content").toString() : "";

        String[] palabras = mensaje.split(" ");
        StringBuilder mensajeSinHashtags = new StringBuilder();
        List<String> hashtags = new ArrayList<>();
        for (String palabra : palabras) {
            if (palabra.startsWith("#")) {
                hashtags.add(palabra);
            } else {
                mensajeSinHashtags.append(palabra).append(" ");
            }
        }
        String mensajeFinal = mensajeSinHashtags.toString().trim();
        holder.contentTextView.setText(mensajeFinal.isEmpty() ? mensaje : mensajeFinal);

        if (!hashtags.isEmpty()) {
            String hashtagsText = String.join(" ", hashtags);
            SpannableString spannable = new SpannableString(hashtagsText);
            // Para cada hashtag, aplicamos un clickable span
            for (final String tag : hashtags) {
                int start = hashtagsText.indexOf(tag);
                int end = start + tag.length();
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        Bundle bundle = new Bundle();
                        bundle.putString("hashtag", tag);
                        navProvider.navigate(R.id.hashtagsFragment, bundle);
                    }
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(Color.BLUE);
                        ds.setUnderlineText(false);
                    }
                };
                spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            holder.hashtagsTextView.setText(spannable);
            holder.hashtagsTextView.setMovementMethod(LinkMovementMethod.getInstance());
            holder.hashtagsTextView.setVisibility(View.VISIBLE);
        } else {
            holder.hashtagsTextView.setVisibility(View.GONE);
        }

        if (post.get("mediaUrl") != null && !post.get("mediaUrl").toString().isEmpty()) {
            holder.mediaImageView.setVisibility(View.VISIBLE);
            if ("audio".equals(post.get("mediaType").toString())) {
                Glide.with(context).load(R.drawable.audio).centerCrop().into(holder.mediaImageView);
            } else {
                Glide.with(context).load(post.get("mediaUrl").toString()).centerCrop().into(holder.mediaImageView);
            }
            holder.mediaImageView.setOnClickListener(view -> {
                appViewModel.postSeleccionado.setValue(post);
                navProvider.navigate(R.id.mediaFragment, null);
            });
        } else {
            holder.mediaImageView.setVisibility(View.GONE);
        }

        // Manejo de likes
        List<String> likes = (List<String>) post.get("likes");
        if (likes == null) {
            likes = new ArrayList<>();
        }
        if (likes.contains(userId)) {
            holder.likeImageView.setImageResource(R.drawable.like_on);
        } else {
            holder.likeImageView.setImageResource(R.drawable.like_off);
        }
        holder.numLikesTextView.setText(String.valueOf(likes.size()));
        List<String> finalLikes = likes;
        holder.likeImageView.setOnClickListener(view -> {
            Databases databases = new Databases(client);
            Handler mainHandler = new Handler(Looper.getMainLooper());
            List<String> nuevosLikes = new ArrayList<>(finalLikes);
            if (nuevosLikes.contains(userId)) {
                nuevosLikes.remove(userId);
            } else {
                nuevosLikes.add(userId);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("likes", nuevosLikes);
            try {
                databases.updateDocument(
                        context.getString(R.string.APPWRITE_DATABASE_ID),
                        context.getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                        postId,
                        data,
                        new ArrayList<>(),
                        new CoroutineCallback<Document>((result, error) -> {
                            if (error != null) {
                                error.printStackTrace();
                                return;
                            }
                            mainHandler.post(() -> postsUpdatedListener.actualizarPosts());
                        })
                );
            } catch (AppwriteException e) {
                e.printStackTrace();
            }
        });

        holder.btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String contenido = post.get("content") != null ? post.get("content").toString() : "Sin contenido";
            shareIntent.putExtra(Intent.EXTRA_TEXT, contenido);
            Intent chooser = Intent.createChooser(shareIntent, "Compartir publicación");
            context.startActivity(chooser);
        });

        if (post.get("uid") != null && post.get("uid").toString().equals(userId)) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Eliminar Post")
                        .setMessage("¿Seguro que deseas eliminar este post?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            final Map<String, Object> deletedPost = new HashMap<>(post);
                            DeletePost.deletePost(client, postId, context, success -> {
                                if (success) {
                                    postsUpdatedListener.actualizarPosts();
                                }
                            });
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        } else {
            holder.btnDelete.setVisibility(View.GONE);
        }


    }

    @Override
    public int getItemCount() {
        return lista == null ? 0 : lista.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView, btnDelete, btnShare;
        TextView authorTextView, contentTextView, numLikesTextView, hashtagsTextView, timeTextView;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            hashtagsTextView = itemView.findViewById(R.id.hashtagsTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnShare = itemView.findViewById(R.id.btnShare);
        }
    }
}
